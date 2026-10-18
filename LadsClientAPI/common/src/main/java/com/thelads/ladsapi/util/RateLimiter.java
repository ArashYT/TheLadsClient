package com.thelads.ladsapi.util;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Sliding window rate limiter (max 8 messages per second per player per PROTOCOL.md).
 */
public final class RateLimiter {
    private final int maxPerSecond;
    private final Deque<Long> timestamps = new ArrayDeque<>();

    public RateLimiter(int maxPerSecond) {
        this.maxPerSecond = maxPerSecond;
    }

    public synchronized boolean tryAcquire() {
        return tryAcquire(System.currentTimeMillis());
    }

    public synchronized boolean tryAcquire(long nowMs) {
        long windowStart = nowMs - 1000L;
        while (!timestamps.isEmpty() && timestamps.peekFirst() <= windowStart) {
            timestamps.pollFirst();
        }
        if (timestamps.size() >= maxPerSecond) {
            return false;
        }
        timestamps.addLast(nowMs);
        return true;
    }
}
