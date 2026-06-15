/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 */
package com.thelads.core.client;

import java.util.ArrayDeque;
import net.minecraft.client.Minecraft;

public class CpsTracker {
    private static final CpsTracker INSTANCE = new CpsTracker();
    private final ArrayDeque<Long> left = new ArrayDeque();
    private final ArrayDeque<Long> right = new ArrayDeque();
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
        if (l && !this.lastLeft) {
            this.left.addLast(now);
        }
        if (r && !this.lastRight) {
            this.right.addLast(now);
        }
        this.lastLeft = l;
        this.lastRight = r;
        this.prune(this.left, now);
        this.prune(this.right, now);
    }

    private void prune(ArrayDeque<Long> q, long now) {
        while (!q.isEmpty() && now - q.peekFirst() > 1000L) {
            q.pollFirst();
        }
    }

    public int leftCps() {
        this.prune(this.left, System.currentTimeMillis());
        return this.left.size();
    }

    public int rightCps() {
        this.prune(this.right, System.currentTimeMillis());
        return this.right.size();
    }
}

