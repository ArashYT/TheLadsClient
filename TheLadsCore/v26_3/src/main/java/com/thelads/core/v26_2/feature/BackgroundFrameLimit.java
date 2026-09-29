package com.thelads.core.v26_2.feature;

/** No sleeps or ticking changes: this selects only the renderer's frame budget. */
public final class BackgroundFrameLimit {
    private boolean backgrounded;
    private long backgroundSince;

    public int apply(int vanilla, int configured, boolean enabled, int mode, boolean focused,
                     boolean hidden, int unfocusedFps, int hiddenFps, long now) {
        if (!enabled || mode == 2) {
            backgrounded = false;
            return vanilla;
        }
        int foreground = Math.max(1, configured);
        if (focused && !hidden) {
            backgrounded = false;
            return foreground;
        }
        if (!backgrounded || now < backgroundSince) {
            backgrounded = true;
            backgroundSince = now;
        }
        if (hidden) return Math.min(foreground, Math.max(1, hiddenFps));
        // Balanced avoids throttling during short Alt-Tab transitions. Hidden windows always throttle.
        if (mode == 1 && now - backgroundSince < 3_000_000_000L) return foreground;
        return Math.min(foreground, Math.max(1, unfocusedFps));
    }
}
