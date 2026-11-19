package com.thelads.ladsapi.api;

import com.google.gson.JsonObject;
import com.thelads.ladsapi.session.Features;
import com.thelads.ladsapi.session.Wire;

/**
 * Sound cues from sound events the client already has (PROTOCOL.md section 17); nothing is fetched. At most 8 loops are kept
 * per player. {@code lp.audio().musicPause().play(...)}: {@link #musicPause()} and {@link #ms(int)} apply to the calls after
 * them on this object. Every call is false without the {@code audio} cap.
 */
public final class Audio {
    public static final int MAX_LOOPS = 8;
    private final Features features;
    private boolean musicPause;
    private int ms = 600_000;

    public Audio(Features features) { this.features = features; }

    /** Vanilla background music is held quiet while the sound plays. */
    public Audio musicPause() { musicPause = true; return this; }

    /** How long a loop lasts at most: 1..3,600,000 (default 600,000). */
    public Audio ms(int ms) { this.ms = ms; return this; }

    /** In the player's ears. {@code category}: music, ambient or sfx. */
    public boolean play(String id, String sound, String category, double volume, double pitch, boolean loop, int fadeMs) {
        return play(id, sound, category, volume, pitch, loop, fadeMs, Double.NaN, Double.NaN, Double.NaN);
    }

    /** At a position (all three finite), with the sound's normal attenuation. */
    public boolean play(String id, String sound, String category, double volume, double pitch, boolean loop, int fadeMs,
                        double x, double y, double z) {
        String aid = Wire.id(id, 32);
        String snd = Wire.resource(sound, 128);
        if (aid == null || snd == null || snd.contains("//") || snd.startsWith("http")) return false;
        JsonObject o = Wire.obj("audio", "play");
        o.addProperty("id", aid);
        o.addProperty("sound", snd);
        o.addProperty("category", "music".equals(category) || "ambient".equals(category) ? category : "sfx");
        o.addProperty("volume", Wire.round(Wire.num(volume, 0, 1, 1), 3));
        o.addProperty("pitch", Wire.round(Wire.num(pitch, 0.5, 2, 1), 3));
        if (loop) o.addProperty("loop", true);
        if (fadeMs > 0) o.addProperty("fade_ms", Math.min(10000, fadeMs));
        if (Wire.finite(x) && Wire.finite(y) && Wire.finite(z)) {
            o.addProperty("x", Wire.round(x, 3));
            o.addProperty("y", Wire.round(y, 3));
            o.addProperty("z", Wire.round(z, 3));
        }
        if (musicPause) o.addProperty("music_pause", true);
        String key = "au:" + aid;
        if (!loop) {
            if (!features.ok("audio")) return false;
            features.remove("audio", key, null); // a one-off with the id of a loop replaces it
            return features.send("audio", o);
        }
        int m = Wire.whole(ms, 1, 3_600_000, 600_000);
        o.addProperty("ms", m);
        if (!features.has(key) && features.count("au:") >= MAX_LOOPS) return false;
        return features.keep("audio", key, o, m, false, Features.countdown("ms"));
    }

    /** Moves a playing sound's volume (0..1) over {@code fadeMs} (1..30000); 0 stops it afterwards. */
    public boolean fade(String id, double volume, int fadeMs) {
        String aid = Wire.id(id, 32);
        if (aid == null) return false;
        double v = Wire.num(volume, 0, 1, 1);
        JsonObject m = Wire.obj("audio", "fade");
        m.addProperty("id", aid);
        m.addProperty("volume", Wire.round(v, 3));
        m.addProperty("fade_ms", Wire.whole(fadeMs, 1, 30000, 1000));
        if (!features.send("audio", m)) return false;
        JsonObject kept = features.kept("au:" + aid);
        if (kept != null) {
            if (v <= 0) {
                features.remove("audio", "au:" + aid, null);
            } else {
                JsonObject c = kept.deepCopy();
                c.addProperty("volume", Wire.round(v, 3));
                c.remove("fade_ms");
                features.rekeep("au:" + aid, c);
            }
        }
        return true;
    }

    /** Stops one sound (fading out over {@code fadeMs}, 0..10000). */
    public boolean stop(String id, int fadeMs) {
        String aid = Wire.id(id, 32);
        if (aid == null) return false;
        JsonObject m = Wire.obj("audio", "stop");
        m.addProperty("id", aid);
        if (fadeMs > 0) m.addProperty("fade_ms", Math.min(10000, fadeMs));
        if (!features.send("audio", m)) return false;
        features.remove("audio", "au:" + aid, null);
        return true;
    }

    /** Stops everything this server started. */
    public boolean stopAll() {
        if (!features.send("audio", Wire.obj("audio", "stop"))) return false;
        features.removeAll("audio", "au:", null);
        return true;
    }
}
