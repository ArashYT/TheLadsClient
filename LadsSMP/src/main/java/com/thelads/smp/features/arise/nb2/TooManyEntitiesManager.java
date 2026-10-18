package com.thelads.smp.features.arise.nb2;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;

/**
 * Spatial entity density render limiter: skips rendering mobs in dense clusters (e.g. > 64 entities in 16x16 blocks)
 * to avoid client FPS drops while keeping mobs alive and ticking on the server (Too Many Entities).
 *
 * Frame counts are strictly reset at the start of each level render frame (via MixinGameRenderer).
 */
public final class TooManyEntitiesManager {
    private static final Long2IntOpenHashMap FRAME_COUNTS = new Long2IntOpenHashMap(256);

    private TooManyEntitiesManager() {}

    public static boolean isTooManyEntitiesEnabled() {
        return NB2Config.get().tooManyEntities;
    }

    public static synchronized boolean shouldSuppress(Entity entity) {
        if (!isTooManyEntitiesEnabled() || entity == null) {
            return false;
        }

        // Never cull players or major bosses
        if (entity instanceof Player || entity instanceof EnderDragon || entity instanceof WitherBoss) {
            return false;
        }

        int cellX = ((int) Math.floor(entity.getX())) >> 4;
        int cellZ = ((int) Math.floor(entity.getZ())) >> 4;
        return shouldSuppressCell(cellX, cellZ);
    }

    public static synchronized boolean shouldSuppressCell(int cellX, int cellZ) {
        if (!isTooManyEntitiesEnabled()) {
            return false;
        }
        long key = (((long) cellX) << 32) | (cellZ & 0xFFFFFFFFL);
        int count = FRAME_COUNTS.addTo(key, 1) + 1;
        int limit = NB2Config.get().maxEntityCount;
        return count > limit;
    }

    public static synchronized void reset() {
        FRAME_COUNTS.clear();
    }
}
