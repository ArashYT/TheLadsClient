package com.thelads.core.client;

import java.util.ArrayDeque;

/**
 * Universal clicks-per-second tracker.
 */
public class CpsTracker {
    private static final CpsTracker INSTANCE = new CpsTracker();

    private static final int CAPACITY = 256;
    private static final int MASK = CAPACITY - 1;

    private final long[] left = new long[CAPACITY];
    private int leftHead = 0;
    private int leftTail = 0;

    private final long[] right = new long[CAPACITY];
    private int rightHead = 0;
    private int rightTail = 0;

    private boolean lastLeft = false;
    private boolean lastRight = false;

    public static CpsTracker get() {
        return INSTANCE;
    }

    public synchronized void tick(boolean attackDown, boolean useDown) {
        long now = System.currentTimeMillis();
        if (attackDown && !lastLeft) addLeft(now);
        if (useDown && !lastRight) addRight(now);
        lastLeft = attackDown;
        lastRight = useDown;
        pruneLeft(now);
        pruneRight(now);
    }

    public synchronized void recordLeftClick() {
        long now = System.currentTimeMillis();
        addLeft(now);
        pruneLeft(now);
    }

    public synchronized void recordRightClick() {
        long now = System.currentTimeMillis();
        addRight(now);
        pruneRight(now);
    }

    private void addLeft(long now) {
        if (leftTail - leftHead >= CAPACITY) leftHead++;
        left[leftTail++ & MASK] = now;
    }

    private void addRight(long now) {
        if (rightTail - rightHead >= CAPACITY) rightHead++;
        right[rightTail++ & MASK] = now;
    }

    private void pruneLeft(long now) {
        while (leftHead < leftTail && now - left[leftHead & MASK] > 1000L) {
            leftHead++;
        }
        if (leftHead == leftTail) {
            leftHead = 0;
            leftTail = 0;
        }
    }

    private void pruneRight(long now) {
        while (rightHead < rightTail && now - right[rightHead & MASK] > 1000L) {
            rightHead++;
        }
        if (rightHead == rightTail) {
            rightHead = 0;
            rightTail = 0;
        }
    }

    public synchronized int leftCps() {
        pruneLeft(System.currentTimeMillis());
        return leftTail - leftHead;
    }

    public synchronized int rightCps() {
        pruneRight(System.currentTimeMillis());
        return rightTail - rightHead;
    }
}
