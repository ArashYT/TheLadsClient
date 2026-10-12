package com.thelads.core.client.hud;

import com.thelads.core.config.HudSettings;

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
    /** Primitive open-addressing table to record what is drawn in each build without boxing or allocations. */
    private static final class DrawTracker {
        int[] keys = new int[64];
        int[] counts = new int[64];
        int[] hashes = new int[64];
        boolean[] occupied = new boolean[64];
        int mask = 63;
        int size = 0;

        void clear() {
            if (size > 0) {
                java.util.Arrays.fill(occupied, false);
                size = 0;
            }
        }

        void record(int what, int how) {
            if ((size + 1) * 2 > keys.length) resize();
            int idx = hash(what) & mask;
            while (occupied[idx]) {
                if (keys[idx] == what) {
                    counts[idx]++;
                    hashes[idx] = 31 * hashes[idx] + how;
                    return;
                }
                idx = (idx + 1) & mask;
            }
            occupied[idx] = true;
            keys[idx] = what;
            counts[idx] = 1;
            hashes[idx] = how;
            size++;
        }

        int find(int what) {
            if (size == 0) return -1;
            int idx = hash(what) & mask;
            while (occupied[idx]) {
                if (keys[idx] == what) return idx;
                idx = (idx + 1) & mask;
            }
            return -1;
        }

        private void resize() {
            int newCap = keys.length * 2;
            int[] nk = new int[newCap];
            int[] nc = new int[newCap];
            int[] nh = new int[newCap];
            boolean[] no = new boolean[newCap];
            int nm = newCap - 1;
            for (int i = 0; i < keys.length; i++) {
                if (occupied[i]) {
                    int idx = hash(keys[i]) & nm;
                    while (no[idx]) idx = (idx + 1) & nm;
                    no[idx] = true;
                    nk[idx] = keys[i];
                    nc[idx] = counts[i];
                    nh[idx] = hashes[i];
                }
            }
            keys = nk;
            counts = nc;
            hashes = nh;
            occupied = no;
            mask = nm;
        }

        private static int hash(int h) {
            h ^= (h >>> 16);
            h *= 0x85ebca6b;
            h ^= (h >>> 13);
            return h;
        }
    }

    /** For each thing drawn (by what it is) in this build and the last: how many times, and where and how, as one number. */
    private static DrawTracker drawing = new DrawTracker(), drawn = new DrawTracker();
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
        drawing.record(what, how);
    }

    /**
     * The current build is done. Something the last build drew too but otherwise (moved, resized, turned, faded, recoloured, another
     * frame of a flipbook) is a step of an animation when the build before moved something too, or while animating: the HUD is then
     * built at least 60 times a second. A one-off step (a new chat line pushes the others up) costs one early build; new content
     * (a text or item the last build did not draw) none.
     */
    public static void built(long now) {
        boolean moving = false;
        if (drawn.size > 0 && drawing.size > 0) {
            for (int i = 0; i < drawing.keys.length; i++) {
                if (drawing.occupied[i]) {
                    int what = drawing.keys[i];
                    int bIdx = drawn.find(what);
                    if (bIdx >= 0 && (drawn.counts[bIdx] != drawing.counts[i] || drawn.hashes[bIdx] != drawing.hashes[i])) {
                        moving = true;
                        break;
                    }
                }
            }
        }
        if (moving) {
            changes++;
            if (moved || now < animatingUntil) animatingUntil = now + HOLD;
        }
        moved = moving;
        DrawTracker previous = drawn;
        drawn = drawing;
        drawing = previous;
        drawing.clear();
    }

    /**
     * Whether this frame rebuilds the HUD: at the cap rate (60 FPS or more while animating), and at once after a resize, a
     * {@link #reset()}, or when the tab list or F3 covers or uncovers a Lads HUD (HudManager.coverChanged).
     */
    public static boolean due(long now, int scaledWidth, int scaledHeight) {
        frames++;
        if (HudManager.getInstance().coverChanged()) last = 0;
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
