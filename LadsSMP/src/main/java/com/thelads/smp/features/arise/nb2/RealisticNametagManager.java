package com.thelads.smp.features.arise.nb2;

import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Hides player overhead nametags whenever solid blocks, walls, or terrain
 * obstruct direct line-of-sight between camera and target player (Realistic Nametag).
 * Caches line-of-sight checks per entity for ~3 ticks (150ms) to eliminate per-frame GC churn.
 */
public final class RealisticNametagManager {
    private static final long CACHE_DURATION_MS = 150L;
    private static final Int2LongOpenHashMap CACHE = new Int2LongOpenHashMap();
    private static long lastPruneTime = 0L;

    private RealisticNametagManager() {}

    public static boolean isRealisticNametagEnabled() {
        return NB2Config.get().realisticNametag;
    }

    public static void clearCache() {
        synchronized (CACHE) {
            CACHE.clear();
        }
    }

    @Environment(EnvType.CLIENT)
    public static boolean isObstructed(Vec3 cameraPos, Vec3 targetPos, boolean isSpectator) {
        return isObstructed(0, cameraPos, targetPos, isSpectator);
    }

    @Environment(EnvType.CLIENT)
    public static boolean isObstructed(int entityId, Vec3 cameraPos, Vec3 targetPos, boolean isSpectator) {
        if (!isRealisticNametagEnabled()) {
            return false;
        }

        // If target is in spectator mode and config says to disable in spectator
        if (isSpectator && NB2Config.get().realisticNametagSpectator) {
            return false;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return false;
        }

        long now = System.currentTimeMillis();

        if (entityId != 0) {
            synchronized (CACHE) {
                if (CACHE.containsKey(entityId)) {
                    long entry = CACHE.get(entityId);
                    long expireAt = entry & 0x7FFFFFFFFFFFFFFFL;
                    if (now < expireAt) {
                        return (entry >>> 63) == 1L;
                    }
                }
            }
        }

        // Clip raycast from camera to nametag position
        ClipContext context = new ClipContext(
                cameraPos,
                targetPos,
                ClipContext.Block.VISUAL,
                ClipContext.Fluid.NONE,
                CollisionContext.empty()
        );

        BlockHitResult hit = mc.level.clip(context);
        boolean obstructed = hit.getType() != HitResult.Type.MISS;

        if (entityId != 0) {
            synchronized (CACHE) {
                long newEntry = (obstructed ? (1L << 63) : 0L) | ((now + CACHE_DURATION_MS) & 0x7FFFFFFFFFFFFFFFL);
                CACHE.put(entityId, newEntry);

                // Periodic prune of expired entries
                if (now - lastPruneTime > 5000L && CACHE.size() > 64) {
                    lastPruneTime = now;
                    CACHE.int2LongEntrySet().removeIf(e -> (e.getLongValue() & 0x7FFFFFFFFFFFFFFFL) < now);
                }
            }
        }

        return obstructed;
    }
}
