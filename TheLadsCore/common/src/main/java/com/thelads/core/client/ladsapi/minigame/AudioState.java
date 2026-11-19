package com.thelads.core.client.ladsapi.minigame;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Audio cues (PROTOCOL.md section 17): sound events the client already has, played through {@link Hooks#sound}. No URL, file or
 * stream is ever fetched. The version calls {@code Minigame.tick(now)} every client tick so fades, loop ends and one-shot ends
 * are applied. Game thread only.
 */
public final class AudioState {
    public static final int MAX = 8;
    private static final int MAX_MS = 3_600_000;

    /** One running cue. Volume 0-1 is before the player's own slider and follows a linear ramp ({@link #volumeAt}). */
    public static final class Voice {
        public final String id, sound, category;
        public final double volume, pitch;
        public final boolean loop, positional, musicPause;
        public final double x, y, z;
        final long start;
        final int ms;
        double rampFrom, rampTo, lastSent = -1;
        long rampStart;
        int rampMs;
        boolean stopping;

        Voice(String id, String sound, String category, double volume, double pitch, boolean loop, boolean positional, double x,
              double y, double z, boolean musicPause, long start, int ms, int fadeIn) {
            this.id = id;
            this.sound = sound;
            this.category = category;
            this.volume = volume;
            this.pitch = pitch;
            this.loop = loop;
            this.positional = positional;
            this.x = x;
            this.y = y;
            this.z = z;
            this.musicPause = musicPause;
            this.start = start;
            this.ms = ms;
            this.rampFrom = fadeIn > 0 ? 0 : volume;
            this.rampTo = volume;
            this.rampStart = start;
            this.rampMs = fadeIn;
        }

        /** The volume to play at now, 0-1. */
        public double volumeAt(long now) {
            if (rampMs <= 0 || now >= rampStart + rampMs) return rampTo;
            if (now <= rampStart) return rampFrom;
            return rampFrom + (rampTo - rampFrom) * (now - rampStart) / (double) rampMs;
        }

        void ramp(double to, int fadeMs, long now) {
            rampFrom = volumeAt(now);
            rampTo = to;
            rampStart = now;
            rampMs = fadeMs;
        }

        boolean rampDone(long now) {
            return rampMs <= 0 || now >= rampStart + rampMs;
        }
    }

    private final Map<String, Voice> voices = new LinkedHashMap<>();
    private boolean held;

    public List<Voice> voices() {
        return new ArrayList<>(voices.values());
    }

    /** Silences everything at once (reset, server switch, disconnect). */
    public void clear() {
        Hooks.SoundPlayer p = Hooks.sound;
        if (p != null) {
            for (Voice v : voices.values()) p.stop(v.id);
            if (held) p.musicHold(false);
        }
        voices.clear();
        held = false;
    }

    public void apply(JsonObject json, long now) {
        String op = Wire.str(json, "op");
        if (op == null) return;
        Hooks.SoundPlayer p = Hooks.sound;
        if (p == null) return;
        switch (op) {
            case "play" -> play(json, now, p);
            case "fade" -> {
                Voice v = voices.get(String.valueOf(Wire.id(json, "id", 32)));
                if (v == null) return;
                double vol = Wire.num(json, "volume", 0, 1, v.volumeAt(now));
                v.ramp(vol, Wire.whole(json, "fade_ms", 1, 30000, 1), now);
                v.stopping = vol <= 0;
            }
            case "stop" -> {
                int fade = Wire.whole(json, "fade_ms", 0, 10000, 0);
                if (json.has("id")) {
                    String id = Wire.id(json, "id", 32);
                    Voice v = id == null ? null : voices.get(id);
                    if (v != null) stop(v, fade, now, p);
                } else {
                    for (Voice v : new ArrayList<>(voices.values())) stop(v, fade, now, p);
                }
            }
            default -> { }
        }
        refreshHold(p);
    }

    private void play(JsonObject json, long now, Hooks.SoundPlayer p) {
        String id = Wire.id(json, "id", 32);
        String sound = Wire.resource(Wire.str(json, "sound"), 128);
        if (id == null || sound == null) return;
        if (!voices.containsKey(id) && voices.size() >= MAX) return;
        String cat = Wire.str(json, "category");
        if (!"music".equals(cat) && !"ambient".equals(cat)) cat = "sfx";
        Double x = Wire.num(json, "x"), y = Wire.num(json, "y"), z = Wire.num(json, "z");
        boolean pos = x != null && y != null && z != null;
        Voice v = new Voice(id, sound, cat, Wire.num(json, "volume", 0, 1, 1), Wire.num(json, "pitch", 0.5, 2, 1),
            Wire.flag(json, "loop", false), pos, pos ? x : 0, pos ? y : 0, pos ? z : 0, Wire.flag(json, "music_pause", false), now,
            Wire.whole(json, "ms", 1, MAX_MS, 600_000), Wire.whole(json, "fade_ms", 0, 10000, 0));
        Voice old = voices.remove(id);
        if (old != null) p.stop(id); // the same id restarts
        if (p.play(v, v.volumeAt(now))) {
            voices.put(id, v);
            v.lastSent = v.volumeAt(now);
        }
    }

    private void stop(Voice v, int fade, long now, Hooks.SoundPlayer p) {
        if (fade <= 0) {
            p.stop(v.id);
            voices.remove(v.id);
        } else {
            v.ramp(0, fade, now);
            v.stopping = true;
        }
    }

    /** Applies fades, ends loops after their ms, drops one-shots that finished. Call every client tick. */
    public void tick(long now) {
        Hooks.SoundPlayer p = Hooks.sound;
        if (p == null) {
            voices.clear();
            return;
        }
        for (Voice v : new ArrayList<>(voices.values())) {
            double vol = v.volumeAt(now);
            if (v.stopping && v.rampDone(now) || now - v.start >= v.ms || (!v.loop && now > v.start && !p.playing(v.id))) {
                p.stop(v.id);
                voices.remove(v.id);
            } else if (Math.abs(vol - v.lastSent) > 0.001) {
                p.volume(v.id, vol);
                v.lastSent = vol;
            }
        }
        refreshHold(p);
    }

    private void refreshHold(Hooks.SoundPlayer p) {
        boolean want = false;
        for (Voice v : voices.values()) want |= v.musicPause;
        if (want != held) {
            held = want;
            p.musicHold(want);
        }
    }
}
