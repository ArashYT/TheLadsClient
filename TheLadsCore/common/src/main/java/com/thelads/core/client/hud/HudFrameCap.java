package com.thelads.core.client.hud;

import com.thelads.core.config.HudSettings;
import java.util.HashMap;
import java.util.Map;

/**
 * The HUD FPS cap's schedule. A capped HUD is rebuilt only on due frames and its last build is drawn on every frame in between,
 * so it stays on screen. Versions that capture the whole HUD (vanilla, mods and the Lads HUD) set {@link #wholeHud} while
 * rebuilding, and the Lads HUD inside then draws directly; elsewhere {@link HudManager} caches the Lads HUD itself.
 * <p>
 * Animations stay smooth: each build reports what it draws and where and how ({@link #draw}). While something on the HUD keeps
 * moving, resizing, turning, fading or changing colour from build to build, the HUD is built at least 60 times a second; a quarter
 * of a second after the last such step, the cap applies again. New content (a number, a timer, a chat line) is not an animation:
 * it shows at the cap rate.
 */
public final class HudFrameCap {
    /** While the HUD animates, it is built at least this often, until this long after its last animated step. */
    private static final long ANIMATING = 1_000_000_000L / 60, HOLD = 250_000_000L;
    private static long last, animatingUntil;
    private static int width, height, frames, steps = 1;
    /** The last build moved something: the next one comes at the animation rate to see whether it keeps moving. */
    private static boolean moved;
    /** For each thing drawn (by what it is) in this build and the last: how many times, and where and how, as one number. */
    private static Map<Integer, int[]> drawing = new HashMap<Integer, int[]>(), drawn = new HashMap<Integer, int[]>();
    /** QA: builds, and builds in which something kept on the HUD moved, resized, turned, faded or changed colour. */
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

    /**
     * One thing the current build draws: {@code what} it is (its text, item, texture or kind) and {@code how} (where, its size,
     * rotation, colour and opacity).
     */
    public static void draw(int what, int how) {
        int[] seen = drawing.get(what);
        if (seen == null) drawing.put(what, seen = new int[2]);
        seen[0]++;
        seen[1] = 31 * seen[1] + how;
    }

    /**
     * The current build is done. Something the last build drew too but otherwise (moved, resized, turned, faded, recoloured, another
     * frame of a flipbook) is a step of an animation when the build before moved something too, or while animating: the HUD is then
     * built at least 60 times a second. A one-off step (a new chat line pushes the others up) costs one early build; new content
     * (a text or item the last build did not draw) none.
     */
    public static void built(long now) {
        boolean moving = false;
        for (Map.Entry<Integer, int[]> thing : drawing.entrySet()) {
            int[] before = drawn.get(thing.getKey());
            if (before != null && (before[0] != thing.getValue()[0] || before[1] != thing.getValue()[1])) { moving = true; break; }
        }
        if (moving) {
            changes++;
            if (moved || now < animatingUntil) animatingUntil = now + HOLD;
        }
        moved = moving;
        Map<Integer, int[]> previous = drawn;
        drawn = drawing;
        drawing = previous;
        drawing.clear();
    }

    /** Whether this frame rebuilds the HUD: at the cap rate (60 FPS or more while animating), and at once after a resize or {@link #reset()}. */
    public static boolean due(long now, int scaledWidth, int scaledHeight) {
        frames++;
        long interval = 1_000_000_000L / Math.max(1, HudSettings.getInstance().getHudFpsLimit()), since = now - last;
        if (moved || now < animatingUntil) interval = Math.min(interval, ANIMATING);
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
        moved = false;
        drawing.clear();
        drawn.clear();
    }
}
