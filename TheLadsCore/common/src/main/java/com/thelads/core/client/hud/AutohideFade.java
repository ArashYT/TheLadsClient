package com.thelads.core.client.hud;

/** Autohide opacity step, kept pure so its frame-rate behaviour is unit tested. */
public final class AutohideFade {
    private AutohideFade() {}
    /**
     * Moves opacity toward target by elapsed/duration, with elapsed capped at 0.1 s. Each call advances by the time since the previous
     * call, so the hotbar and Lads HUD calls in one frame add up to a single frame's step. Only a fade-out snaps its invisible tail to 0:
     * snapping both ways reset every fade-in step below 0.02 (any frame under 7 ms at 350 ms), so the HUD never came back above ~143 FPS.
     */
    public static float step(float opacity, float target, double elapsedSeconds, double durationSeconds) {
        float step = durationSeconds <= 0 ? 1 : (float) (Math.min(.1, elapsedSeconds) / durationSeconds);
        float next = target > opacity ? Math.min(target, opacity + step) : Math.max(target, opacity - step);
        return target == 0 && next < .02f ? 0 : next;
    }
    /** ARGB with only its alpha scaled: fills, text and straight-alpha sprites. */
    public static int tint(int color, float alpha) { return (color & 0xFFFFFF) | Math.round((color >>> 24) * alpha) << 24; }
    /** Every channel scaled: premultiplied-alpha blits (the GUI item atlas, picture-in-picture textures) then fade instead of brightening. */
    public static int tintPremultiplied(int color, float alpha) {
        int result = 0;
        for (int shift = 0; shift < 32; shift += 8) result |= Math.round((color >>> shift & 255) * alpha) << shift;
        return result;
    }
}
