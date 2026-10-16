package com.thelads.smp.features;

import com.thelads.smp.config.LadsConfig;
import com.thelads.smp.mixin.MixinLivingEntityAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Consolidates 'netherportalfix': Tracks player portal transitions between Overworld and Nether
 * so returning through a portal links back to the original portal the player entered from.
 */
public final class NetherPortalFixManager {
    private static final int MAX_PORTAL_DIST_SQ = 16;
    private static final int MAX_TRACKED_PER_PLAYER = 16;

    public record PortalLink(ResourceKey<Level> fromDim, BlockPos fromPos, ResourceKey<Level> toDim, BlockPos toPos) {}
    private record PendingPortal(ResourceKey<Level> fromDim, BlockPos fromPos) {}

    private static final Map<UUID, Deque<PortalLink>> PLAYER_PORTAL_HISTORY = new ConcurrentHashMap<>();
    private static final Map<UUID, PendingPortal> PENDING_PORTALS = new ConcurrentHashMap<>();

    private NetherPortalFixManager() {}

    public static void init() {
        // Dimension change callback is invoked from MixinServerPlayer#triggerDimensionChangeTriggers
    }

    public static Optional<BlockPos> resolveExitPortal(
            Optional<BlockPos> prevResult,
            ServerLevel destination,
            Entity entity,
            BlockPos enteredPortalPos
    ) {
        if (!LadsConfig.get().features.netherPortalFix || !(entity instanceof ServerPlayer player) || enteredPortalPos == null) {
            return prevResult;
        }

        ResourceKey<Level> currentDim = player.level().dimension();
        ResourceKey<Level> targetDim = destination.dimension();
        if (!isOverworldNetherPair(currentDim, targetDim)) {
            return prevResult;
        }

        PENDING_PORTALS.put(player.getUUID(), new PendingPortal(currentDim, enteredPortalPos.immutable()));

        Deque<PortalLink> history = PLAYER_PORTAL_HISTORY.get(player.getUUID());
        if (history != null) {
            Iterator<PortalLink> it = history.iterator();
            while (it.hasNext()) {
                PortalLink link = it.next();
                if (link.toDim().equals(currentDim)
                        && link.fromDim().equals(targetDim)
                        && link.toPos().distSqr(enteredPortalPos) <= MAX_PORTAL_DIST_SQ) {
                    it.remove();
                    if (destination.getBlockState(link.fromPos()).is(Blocks.NETHER_PORTAL)) {
                        PENDING_PORTALS.remove(player.getUUID());
                        return Optional.of(link.fromPos());
                    }
                }
            }
        }
        return prevResult;
    }

    public static void onPlayerChangedDimension(ServerPlayer player, ServerLevel origin, ServerLevel destination) {
        if (!LadsConfig.get().features.netherPortalFix) return;
        PendingPortal pending = PENDING_PORTALS.remove(player.getUUID());
        if (pending == null || !pending.fromDim().equals(origin.dimension())) {
            return;
        }
        if (!isOverworldNetherPair(origin.dimension(), destination.dimension())) {
            return;
        }
        BlockPos destPortalPos = ((MixinLivingEntityAccessor) player).ladssmp$getLastPos();
        if (destPortalPos == null) {
            destPortalPos = player.blockPosition();
        }
        BlockPos finalDestPos = destPortalPos.immutable();
        Deque<PortalLink> history = PLAYER_PORTAL_HISTORY.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>());
        history.removeIf(link -> link.toDim().equals(destination.dimension()) && link.toPos().distSqr(finalDestPos) <= MAX_PORTAL_DIST_SQ);
        while (history.size() >= MAX_TRACKED_PER_PLAYER) {
            history.removeLast();
        }
        history.addFirst(new PortalLink(origin.dimension(), pending.fromPos(), destination.dimension(), finalDestPos));
    }

    public static boolean isOverworldNetherPair(ResourceKey<Level> a, ResourceKey<Level> b) {
        return (a == Level.OVERWORLD && b == Level.NETHER) || (a == Level.NETHER && b == Level.OVERWORLD);
    }
}
