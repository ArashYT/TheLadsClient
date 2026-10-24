package com.thelads.core.client;

/** Chunk Animator's motion: how far (blocks) a chunk still is from its place, easing out (cubic) over the duration. */
public final class ChunkAnimator {
    /** How far a chunk starts from its place, in blocks. */
    static final float TRAVEL = 32;

    private ChunkAnimator() {}

    /** The Y offset {@code elapsedNanos} after the chunk was first drawn; 0 once it arrived. {@code fromAbove}: Mode From Above. */
    public static float offset(boolean fromAbove, double durationMs, long elapsedNanos) {
        if (elapsedNanos < 0 || durationMs <= 0) return 0;
        double t = elapsedNanos / (durationMs * 1e6);
        if (t >= 1) return 0;
        double left = 1 - t;
        float distance = (float) (TRAVEL * left * left * left);
        return fromAbove ? distance : -distance;
    }
}
