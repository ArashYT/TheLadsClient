package com.thelads.smp.features.arise.nb2;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Spatial entity density render limiter: skips rendering mobs in dense clusters (e.g. > 64 entities in 16x16 blocks)
 * to avoid client FPS drops while keeping mobs alive and ticking on the server (Too Many Entities).
 */
public final class TooManyEntitiesManager {
    private static final Map<Long, Integer> FRAME_COUNTS = new HashMap<>(128);
    private static long currentFrame = 0L;

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

        long nowMs = System.currentTimeMillis();
        long frame = nowMs / 33L; // ~30 fps bucket to reset cell counts smoothly without GC churn

        if (frame != currentFrame) {
            currentFrame = frame;
            FRAME_COUNTS.clear();
        }

        int cellX = ((int) Math.floor(entity.getX())) >> 4;
        int cellZ = ((int) Math.floor(entity.getZ())) >> 4;
        long key = (((long) cellX) << 32) | (cellZ & 0xFFFFFFFFL);

        int count = FRAME_COUNTS.getOrDefault(key, 0) + 1;
        FRAME_COUNTS.put(key, count);

        int limit = NB2Config.get().maxEntityCount;
        return count > limit;
    }

    public static synchronized void reset() {
        FRAME_COUNTS.clear();
    }
}
