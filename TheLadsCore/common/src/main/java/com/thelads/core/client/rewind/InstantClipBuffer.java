package com.thelads.core.client.rewind;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Ring buffer managing timestamped clip intervals for instant rewind clipping (parity with Lunar Rewind).
 */
public final class InstantClipBuffer {
    public record ClipWindow(long startEpochMs, long endEpochMs, int durationSeconds) {}

    private static final int MAX_BUFFER_SAMPLES = 7200; // e.g. 60 FPS * 120s
    private static final Deque<Long> TIMESTAMPS = new ArrayDeque<>();

    private InstantClipBuffer() {}

    public static synchronized void recordTimestamp(long epochMs) {
        if (TIMESTAMPS.size() >= MAX_BUFFER_SAMPLES) {
            TIMESTAMPS.pollFirst();
        }
        TIMESTAMPS.addLast(epochMs);
    }

    public static synchronized ClipWindow triggerClip(long nowMs, int durationSeconds) {
        long durationMs = durationSeconds * 1000L;
        long startMs = Math.max(0, nowMs - durationMs);
        return new ClipWindow(startMs, nowMs, durationSeconds);
    }

    public static synchronized int getSampleCount() {
        return TIMESTAMPS.size();
    }

    public static synchronized void clear() {
        TIMESTAMPS.clear();
    }
}
