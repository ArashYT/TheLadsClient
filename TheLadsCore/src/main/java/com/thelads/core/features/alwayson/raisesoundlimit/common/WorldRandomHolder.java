/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet
 */
package com.thelads.core.features.alwayson.raisesoundlimit.common;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.concurrent.locks.StampedLock;

public class WorldRandomHolder {
    private static final StampedLock lock = new StampedLock();
    private static final ReferenceOpenHashSet<Object> randoms = new ReferenceOpenHashSet(16, 0.5f);

    public static void putWorldRandom(Object random) {
        long stamp = lock.writeLock();
        try {
            randoms.add(random);
        }
        finally {
            lock.unlockWrite(stamp);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static boolean isWorldRandom(Object random) {
        long stamp = lock.readLock();
        try {
            boolean bl = randoms.contains(random);
            return bl;
        }
        finally {
            lock.unlockRead(stamp);
        }
    }
}

