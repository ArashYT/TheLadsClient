package com.thelads.core.client.cosmetics.anim;

import com.thelads.core.client.cosmetics.Emotes;
import com.thelads.core.modules.CosmeticsModule;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The render side of Emotes: for each rendered player, its pose this frame and the blend with vanilla. Blends in over BLEND_IN_MS
 * from whatever the player was doing and out over BLEND_OUT_MS into vanilla's own (walking) pose underneath, so nothing snaps. A
 * finished blend-out returns weight 0 once (the versions put the model back) and drops the player from Emotes.
 * <p>
 * Cost: a player who is not emoting is one map lookup (Emotes.get). Nothing is allocated per frame: each emoting player has one
 * Playback, made when its emote is first rendered. Render thread only.
 */
public final class EmoteAnimator {
    public static final float BLEND_IN_MS = 200, BLEND_OUT_MS = 280;
    /** A remote player who moved this far (blocks) from where its emote started stops it here before the network says so. */
    static final double REMOTE_MOVE = 0.25;
    private static final long BASE_MS = System.currentTimeMillis(), BASE_NANOS = System.nanoTime();
    private static final Map<UUID, Playback> PLAYBACK = new HashMap<>();
    private static CosmeticsModule cosmetics;

    private static final class Playback {
        final EmotePose pose = new EmotePose();
        long recordStart;
        double phaseStart, last, ax, ay, az;
        float ramp;
        boolean anchored;
    }

    private EmoteAnimator() {}

    /** Emotes' clock (System.currentTimeMillis) with nanoTime's smoothness. */
    public static double now() { return BASE_MS + (System.nanoTime() - BASE_NANOS) / 1_000_000.0; }

    /** This frame's pose of a rendered player, or null when it is not emoting. {@code remote}: another player at x, y, z. */
    public static EmotePose pose(UUID player, boolean remote, double x, double y, double z) {
        return pose(player, remote, x, y, z, now());
    }

    static EmotePose pose(UUID player, boolean remote, double x, double y, double z, double now) {
        Emotes.Playing playing = Emotes.get(player);
        if (playing == null) {
            if (!PLAYBACK.isEmpty()) PLAYBACK.remove(player);
            return null;
        }
        if (remote && !showOthers()) return null; // Cosmetics off, or "Show other players' emotes" off: others stay vanilla
        Emote emote = EmoteLibrary.get(playing.id());
        if (emote == null) return null; // an emote this client does not have
        Playback p = PLAYBACK.get(player);
        if (p == null) {
            PLAYBACK.put(player, p = new Playback());
            p.phaseStart = p.recordStart = playing.startMs();
            p.pose.emote = emote;
            p.ramp = playing.stopping() ? clamp(1 - (float) ((now - playing.stopMs()) / BLEND_OUT_MS)) : clamp((float) ((now - playing.startMs()) / BLEND_IN_MS));
            p.last = now;
        } else if (p.recordStart != playing.startMs()) {
            // Started again: the same emote still showing keeps its beat, anything else starts from its first frame.
            if (p.pose.emote != emote || p.ramp == 0) p.phaseStart = playing.startMs();
            p.recordStart = playing.startMs();
            p.pose.emote = emote;
            p.anchored = false;
        }
        boolean out = playing.stopping();
        if (remote && !out) {
            if (!p.anchored) { p.anchored = true; p.ax = x; p.ay = y; p.az = z; }
            else if (sq(x - p.ax) + sq(y - p.ay) + sq(z - p.az) > REMOTE_MOVE * REMOTE_MOVE) out = Emotes.stop(player);
        }
        float dt = (float) Math.max(0, now - p.last);
        p.last = now;
        p.ramp = out ? clamp(p.ramp - dt / BLEND_OUT_MS) : clamp(p.ramp + dt / BLEND_IN_MS);
        EmotePose pose = p.pose;
        pose.weight = p.ramp * p.ramp * (3 - 2 * p.ramp);
        if (out && p.ramp == 0) {
            Emotes.remove(player);
            PLAYBACK.remove(player);
            pose.weight = 0;
            return pose;
        }
        emote.sample(now - p.phaseStart, pose.v);
        return pose;
    }

    /** Cosmetics "Show other players' emotes". */
    static boolean showOthers() {
        CosmeticsModule module = cosmetics;
        if (module == null) cosmetics = module = CosmeticsModule.get();
        return module.showOthers.get();
    }

    private static float clamp(float v) { return v < 0 ? 0 : v > 1 ? 1 : v; }
    private static double sq(double v) { return v * v; }

    /** Tests and QA: forget every playback. */
    static void reset() { PLAYBACK.clear(); }
}
