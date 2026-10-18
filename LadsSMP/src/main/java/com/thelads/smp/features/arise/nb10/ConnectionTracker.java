package com.thelads.smp.features.arise.nb10;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks in-progress hanging connections initiated by players between two blocks.
 */
public final class ConnectionTracker {

    public record PendingConnection(
            ResourceKey<Level> dimension,
            BlockPos pos,
            Direction face,
            ItemStack itemStack,
            long createdTimeMs
    ) {}

    private static final Map<UUID, PendingConnection> PENDING = new ConcurrentHashMap<>();

    private ConnectionTracker() {}

    public static void init() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (handler.player != null) {
                clearPending(handler.player.getUUID());
            }
        });
    }

    public static void setPending(Player player, BlockPos pos, Direction face, ItemStack item) {
        PENDING.put(player.getUUID(), new PendingConnection(
                player.level().dimension(),
                pos.immutable(),
                face,
                item.copy(),
                System.currentTimeMillis()
        ));
    }

    public static PendingConnection getPending(Player player) {
        PendingConnection pending = PENDING.get(player.getUUID());
        if (pending != null) {
            // Expire pending connections older than 2 minutes to prevent stale state
            if (System.currentTimeMillis() - pending.createdTimeMs() > 120_000) {
                PENDING.remove(player.getUUID());
                return null;
            }
        }
        return pending;
    }

    public static void clearPending(Player player) {
        clearPending(player.getUUID());
    }

    public static void clearPending(UUID playerUuid) {
        PENDING.remove(playerUuid);
    }
}
