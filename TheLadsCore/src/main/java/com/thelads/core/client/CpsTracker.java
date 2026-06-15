package com.thelads.core.client;

import net.minecraft.client.Minecraft;

import java.util.ArrayDeque;

/**
 * Tracks left/right clicks-per-second by sampling the attack/use key state once
 * per client tick and counting rising edges within a rolling one-second window.
 * No mixin required.
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

    public void tick(Minecraft mc) {
        if (mc == null || mc.options == null) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean l = mc.options.keyAttack.isDown();
        boolean r = mc.options.keyUse.isDown();
        if (l && !lastLeft) left.addLast(now);
        if (r && !lastRight) right.addLast(now);
        lastLeft = l;
        lastRight = r;
        prune(left, now);
        prune(right, now);
    }

    private void prune(ArrayDeque<Long> q, long now) {
        while (!q.isEmpty() && now - q.peekFirst() > 1000L) {
            q.pollFirst();
        }
    }

    public int leftCps() {
        prune(left, System.currentTimeMillis());
        return left.size();
    }

    public int rightCps() {
        prune(right, System.currentTimeMillis());
        return right.size();
    }
}
