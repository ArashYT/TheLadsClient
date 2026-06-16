/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.level.ChunkPos
 */
package io.papermc.paper.util;

import net.minecraft.world.level.ChunkPos;

public class MCUtil {
    public static long getCoordinateKey(int x, int z) {
        return (long)z << 32 | (long)x & 0xFFFFFFFFL;
    }

    public static long getCoordinateKey(ChunkPos pair) {
        return (long)pair.z() << 32 | (long)pair.x() & 0xFFFFFFFFL;
    }

    public static int getCoordinateX(long key) {
        return (int)key;
    }

    public static int getCoordinateZ(long key) {
        return (int)(key >>> 32);
    }
}

