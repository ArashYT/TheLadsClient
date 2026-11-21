package com.thelads.core.perf;

/**
 * Look-direction bias for the integrated server's chunk send order. Pure math, no Minecraft types.
 * Smaller score = sent earlier. The nearest ring (chunk distance <= {@link #NEAR_RING}) is never reordered.
 */
public final class ChunkPriority {
    public static final int NEAR_RING = 2;
    /** How much of the distance a chunk dead ahead saves. */
    public static final double FRONT_BONUS = 0.5;
    private static final boolean KILLED = "false".equalsIgnoreCase(System.getProperty("lads.chunkPriority"));
    /** Mirrors the "Smart Chunk Priority" option in the Performance module. */
    public static volatile boolean enabled = true;

    private ChunkPriority() {}

    public static boolean on() {
        return enabled && !KILLED;
    }

    /**
     * @param dx chunk x offset from the player chunk
     * @param dz chunk z offset from the player chunk
     * @param yawDeg player yaw in degrees (Minecraft convention: 0 = +Z, 90 = -X)
     */
    public static double score(int dx, int dz, float yawDeg) {
        double distSq = (double) dx * dx + (double) dz * dz;
        if (distSq == 0.0) return 0.0;
        if (Math.max(Math.abs(dx), Math.abs(dz)) <= NEAR_RING) return distSq - 1_000_000.0;
        double yaw = Math.toRadians(yawDeg);
        double lx = -Math.sin(yaw);
        double lz = Math.cos(yaw);
        double cos = (dx * lx + dz * lz) / Math.sqrt(distSq);
        return distSq * (1.0 - FRONT_BONUS * Math.max(0.0, cos));
    }

    /** Squared block distance (center to center) under which nothing is biased: covers the whole nearest ring of chunks. */
    public static final double NEAR_DIST_SQ = 3300.0;

    /**
     * Distance for a mesh-build queue that picks the smallest: {@code distSq} (block units, camera to section center), made
     * smaller for sections in front of the camera. Never below {@link #NEAR_DIST_SQ} once above it, so the nearest sections
     * are never outranked; at or below it the value is unchanged.
     * @param dx section center x minus camera x (blocks)
     * @param dz section center z minus camera z (blocks)
     */
    public static double meshDistance(double distSq, double dx, double dz, float yawDeg) {
        if (distSq <= NEAR_DIST_SQ) return distSq;
        double flat = dx * dx + dz * dz;
        if (flat <= 0.0) return distSq;
        double yaw = Math.toRadians(yawDeg);
        double cos = (dx * -Math.sin(yaw) + dz * Math.cos(yaw)) / Math.sqrt(flat);
        return Math.max(NEAR_DIST_SQ, distSq * (1.0 - FRONT_BONUS * Math.max(0.0, cos)));
    }

    /** Integer sort key for APIs that compare ints. Monotonic in {@link #score}. */
    public static int key(int dx, int dz, float yawDeg) {
        double s = score(dx, dz, yawDeg);
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, Math.round(s * 16.0)));
    }
}
