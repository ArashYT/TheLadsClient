package com.thelads.core.client;

/**
 * Short-lived dynamic light flashes for client-side explosions.
 * An explosion creates a source at full brightness (15) that fades evenly to 0 over ~0.5 s (scaled by explosion power).
 * Uses a reserved negative entity ID range so flashes never clash with real entity IDs.
 * Bounded by MAX_FLASHES and cleared on world change or disconnect to prevent memory leaks.
 */
public final class ExplosionFlashes {
    public static final ExplosionFlashes WORLD = new ExplosionFlashes();
    public static final int MAX_FLASHES = 16;
    public static final int FIRST_ID = -1001;
    public static final int LAST_ID = FIRST_ID - MAX_FLASHES + 1;

    private final int[] ids = new int[MAX_FLASHES];
    private final double[] xs = new double[MAX_FLASHES];
    private final double[] ys = new double[MAX_FLASHES];
    private final double[] zs = new double[MAX_FLASHES];
    private final int[] ages = new int[MAX_FLASHES];
    private final int[] durations = new int[MAX_FLASHES];
    private int count;

    public ExplosionFlashes() {
        for (int i = 0; i < MAX_FLASHES; i++) {
            ids[i] = FIRST_ID - i;
        }
    }

    /**
     * Adds an explosion flash at the specified center position with lifetime scaled by power.
     * Full brightness (15) fading to 0 over ~0.5 s for typical explosions (power ~4.0).
     */
    public void add(double x, double y, double z, float power) {
        if (power <= 0) return;
        int duration = Math.max(4, Math.min(20, Math.round(power * 2.5f)));
        int slot;
        if (count < MAX_FLASHES) {
            slot = count++;
        } else {
            // Cap reached: replace the flash that is closest to expiring (highest age/duration ratio)
            int oldestSlot = 0;
            float oldestRatio = -1;
            for (int i = 0; i < MAX_FLASHES; i++) {
                float ratio = (float) ages[i] / durations[i];
                if (ratio > oldestRatio) {
                    oldestRatio = ratio;
                    oldestSlot = i;
                }
            }
            slot = oldestSlot;
        }
        xs[slot] = x;
        ys[slot] = y;
        zs[slot] = z;
        ages[slot] = 0;
        durations[slot] = duration;
    }

    /** The dynamic luminance (1-15) of a flash slot at its current age, or 0 if expired. */
    public int luminance(int slot) {
        if (slot < 0 || slot >= count) return 0;
        int age = ages[slot], duration = durations[slot];
        if (age >= duration) return 0;
        return Math.max(1, Math.min(15, Math.round(15.0f * (1.0f - (float) age / duration))));
    }

    /** Adds all active explosion flash light sources to this tick's dynamic lights list. */
    public void collect(DynamicLights lights) {
        for (int i = 0; i < count; i++) {
            int lum = luminance(i);
            if (lum > 0) {
                lights.add(ids[i], xs[i], ys[i], zs[i], lum);
            }
        }
    }

    /** Advances age of each active flash by one tick, discarding any that have expired. */
    public void tick() {
        int i = 0;
        while (i < count) {
            ages[i]++;
            if (ages[i] >= durations[i]) {
                // Expired: swap with the last active entry and reuse the slot ID
                count--;
                if (i < count) {
                    xs[i] = xs[count];
                    ys[i] = ys[count];
                    zs[i] = zs[count];
                    ages[i] = ages[count];
                    durations[i] = durations[count];
                }
            } else {
                i++;
            }
        }
    }

    /** Clears all active flashes (e.g. on world change, reset, or disconnect). */
    public void reset() {
        count = 0;
    }

    public int count() { return count; }
    public int id(int index) { return ids[index]; }
    public double x(int index) { return xs[index]; }
    public double y(int index) { return ys[index]; }
    public double z(int index) { return zs[index]; }
    public int age(int index) { return ages[index]; }
    public int duration(int index) { return durations[index]; }
}
