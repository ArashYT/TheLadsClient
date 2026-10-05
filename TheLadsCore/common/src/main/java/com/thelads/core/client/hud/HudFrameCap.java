package com.thelads.core.client.hud;

import com.thelads.core.config.HudSettings;

/**
 * The HUD FPS cap's schedule. A capped HUD is rebuilt only on due frames and its last build is drawn on every frame in between,
 * so it stays on screen. Versions that capture the whole HUD (vanilla, mods and the Lads HUD) set {@link #wholeHud} while
 * rebuilding, and the Lads HUD inside then draws directly; elsewhere {@link HudManager} caches the Lads HUD itself.
 * <p>
 * Animations stay smooth: each build reports a fingerprint of what it drew ({@link #built}). While the HUD changes from one build to
 * the next (a fade, a moving or growing element, a changing number), it is built at least 60 times a second; once it has stayed the
 * same for a quarter of a second, the cap applies again.
 */
public final class HudFrameCap {
    /** While the HUD changes, it is built at least this often, for this long after the last change. */
    private static final long ANIMATING = 1_000_000_000L / 60, HOLD = 250_000_000L;
    private static long last, animatingUntil;
    private static int width, height, frames, steps = 1, print;
    private static boolean printed;
    /** QA: builds, and builds that drew something else than the one before. */
    public static int builds, changes;
    /** True while a version rebuilds the whole HUD under this cap. */
    public static boolean wholeHud;

    private HudFrameCap() {}

    public static boolean enabled() {
        HudSettings settings = HudSettings.getInstance();
        return !settings.isHudFpsUnlimited() && settings.getHudFpsLimit() > 0;
    }

    /**
     * How many frames the current build stands for: the frames since the last build, this one included (1 while the HUD is built every
     * frame). An animation that moves a step each time it is drawn moves this many steps, so it keeps its pace under the cap.
     */
    public static int steps() {
        return steps;
    }

    /** A build drew this: if it differs from the last build, the HUD is animating and is built at 60 FPS or more for a while. */
    public static void built(int fingerprint, long now) {
        if (printed && fingerprint != print) {
            animatingUntil = now + HOLD;
            changes++;
        }
        print = fingerprint;
        printed = true;
    }

    /** Whether this frame rebuilds the HUD: at the cap rate (60 FPS or more while animating), and at once after a resize or {@link #reset()}. */
    public static boolean due(long now, int scaledWidth, int scaledHeight) {
        frames++;
        long interval = 1_000_000_000L / Math.max(1, HudSettings.getInstance().getHudFpsLimit()), since = now - last;
        if (now < animatingUntil) interval = Math.min(interval, ANIMATING);
        if (last != 0 && since < interval && scaledWidth == width && scaledHeight == height) return false;
        steps = frames;
        frames = 0;
        builds++;
        // Keep the cadence on schedule (vsync frames rarely land exactly on it) unless a whole interval was missed.
        last = last != 0 && since >= interval && since < 2 * interval ? last + interval : now;
        width = scaledWidth;
        height = scaledHeight;
        return true;
    }

    /** The next frame rebuilds: the cap was off, or a build failed part-way. */
    public static void reset() {
        last = animatingUntil = 0;
        frames = 0;
        steps = 1;
        printed = false;
    }
}
