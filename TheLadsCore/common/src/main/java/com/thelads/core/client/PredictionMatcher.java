package com.thelads.core.client;

/**
 * Prediction modules (Mace / Sword / Pearl Optimizer) play a sound or effect a moment before the server's own packet arrives.
 * This ring remembers what was predicted (id, position, time) so the server's copy can be dropped once: an id and position
 * match inside a short window. After the window nothing is suppressed, so a wrong guess costs one extra sound, never a missing one.
 * Main thread only; the ring is small and scanned linearly.
 */
public final class PredictionMatcher {
    public static final long DEFAULT_WINDOW_MS = 600;
    public static final double DEFAULT_RADIUS = 2.5;
    private static final int CAPACITY = 16;
    /** One shared ring for sounds and level events predicted by the optimizer modules (main thread only). */
    public static final PredictionMatcher SHARED = new PredictionMatcher();

    private final long windowMs;
    private final double radiusSq;
    private final String[] ids = new String[CAPACITY];
    private final double[] xs = new double[CAPACITY];
    private final double[] ys = new double[CAPACITY];
    private final double[] zs = new double[CAPACITY];
    private final long[] times = new long[CAPACITY];
    private int next;

    public PredictionMatcher() { this(DEFAULT_WINDOW_MS, DEFAULT_RADIUS); }

    public PredictionMatcher(long windowMs, double radius) {
        this.windowMs = windowMs;
        this.radiusSq = radius * radius;
    }

    /** Remember that {@code id} was just played at this position. The oldest entry is overwritten when the ring is full. */
    public void record(String id, double x, double y, double z, long nowMs) {
        ids[next] = id; xs[next] = x; ys[next] = y; zs[next] = z; times[next] = nowMs;
        next = (next + 1) % CAPACITY;
    }

    /**
     * True (and the prediction is used up) when the server's copy of a predicted event should be dropped: the oldest live entry
     * with the same id near the position, else one of the same family ({@link #family}: an id up to its last '.', so a predicted
     * strong hit also absorbs the server's weak or no-damage variant of the same attack).
     */
    public boolean shouldSuppress(String id, double x, double y, double z, long nowMs) {
        int hit = find(id, false, x, y, z, nowMs);
        if (hit < 0) hit = find(id, true, x, y, z, nowMs);
        if (hit < 0) return false;
        ids[hit] = null;
        return true;
    }

    /** Like {@link #shouldSuppress} but uses nothing up: true when a live prediction of this id (or family) is near the position. */
    public boolean peek(String id, double x, double y, double z, long nowMs) {
        return find(id, false, x, y, z, nowMs) >= 0 || find(id, true, x, y, z, nowMs) >= 0;
    }

    private int find(String id, boolean sameFamily, double x, double y, double z, long nowMs) {
        String fam = sameFamily ? family(id) : null;
        int best = -1;
        for (int i = 0; i < CAPACITY; i++) {
            String have = ids[i];
            if (have == null) continue;
            long age = nowMs - times[i];
            if (age > windowMs || age < -windowMs) { ids[i] = null; continue; } // expired (or the clock went backwards)
            if (sameFamily ? !fam.equals(family(have)) : !have.equals(id)) continue;
            double dx = xs[i] - x, dy = ys[i] - y, dz = zs[i] - z;
            if (dx * dx + dy * dy + dz * dz > radiusSq) continue;
            if (best < 0 || times[i] < times[best]) best = i;
        }
        return best;
    }

    /** "entity.player.attack.strong" -> "entity.player.attack"; an id without '.' is its own family. */
    public static String family(String id) {
        int dot = id.lastIndexOf('.');
        return dot < 0 ? id : id.substring(0, dot);
    }

    public void clear() {
        java.util.Arrays.fill(ids, null);
    }
}
