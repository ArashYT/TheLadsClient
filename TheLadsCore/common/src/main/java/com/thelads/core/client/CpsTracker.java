package com.thelads.core.client;

import java.util.ArrayDeque;

/**
 * Universal clicks-per-second tracker.
 */
public class CpsTracker {
    private static final CpsTracker INSTANCE = new CpsTracker();

    private final ArrayDeque<Long> left = new ArrayDeque<>();
    private final ArrayDeque<Long> right = new ArrayDeque<>();
    private boolean lastLeft = false;
    private boolean lastRight = false;

    public static CpsTracker get() {
        return INSTANCE;
    }

    public synchronized void tick(boolean attackDown, boolean useDown) {
        long now = System.currentTimeMillis();
        if (attackDown && !lastLeft) left.addLast(now);
        if (useDown && !lastRight) right.addLast(now);
        lastLeft = attackDown;
        lastRight = useDown;
        prune(left, now);
        prune(right, now);
    }

    public synchronized void recordLeftClick() {
        long now = System.currentTimeMillis();
        left.addLast(now);
        prune(left, now);
    }

    public synchronized void recordRightClick() {
        long now = System.currentTimeMillis();
        right.addLast(now);
        prune(right, now);
    }

    private void prune(ArrayDeque<Long> q, long now) {
        while (!q.isEmpty() && now - q.peekFirst() > 1000L) {
            q.pollFirst();
        }
    }

    public synchronized int leftCps() {
        prune(left, System.currentTimeMillis());
        return left.size();
    }

    public synchronized int rightCps() {
        prune(right, System.currentTimeMillis());
        return right.size();
    }
}
