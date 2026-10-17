package com.thelads.smp.features;

import com.thelads.smp.config.LadsConfig;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Consolidates 'limitedchunks':
 * When players disconnect, tracks their associated forceloaded chunk tickets.
 * If no player returns within the configured timeout (default 360 minutes / 6 hours),
 * the forceloaded tickets are safely unloaded to protect server performance.
 */
public final class LimitedChunksManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP/LimitedChunks");

    // Tracks when each offline player's chunkloading timer expires
    private static final Map<UUID, Long> PENDING_UNLOADS = new ConcurrentHashMap<>();

    private LimitedChunksManager() {}

    public static void init() {
        // Periodic check on server tick (every 100 ticks / 5 seconds)
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!LadsConfig.get().features.limitedChunkloading) return;
            if (server.getTickCount() % 100 != 0) return;
            if (PENDING_UNLOADS.isEmpty()) return;

            long now = System.currentTimeMillis();
            PENDING_UNLOADS.entrySet().removeIf(entry -> {
                UUID playerUuid = entry.getKey();
                long expiry = entry.getValue();

                if (server.getPlayerList().getPlayer(playerUuid) != null) {
                    // Player logged back in; cancel unload
                    return true;
                }

                if (now >= expiry) {
                    // Player has been offline past timeout; check if server has other players
                    boolean hasPlayersOnline = !server.getPlayerList().getPlayers().isEmpty();
                    if (!hasPlayersOnline) {
                        for (ServerLevel level : server.getAllLevels()) {
                            ServerChunkCache chunkSource = level.getChunkSource();
                            LongSet forced = chunkSource.getForceLoadedChunks();
                            if (!forced.isEmpty()) {
                                int count = forced.size();
                                for (long packed : forced.toLongArray()) {
                                    ChunkPos pos = ChunkPos.unpack(packed);
                                    chunkSource.updateChunkForced(pos, false);
                                }
                                LOGGER.info("[Lads SMP/LimitedChunks] Unloaded {} forceloaded chunks in {} due to player offline timeout.",
                                        count, level.dimension().identifier());
                            }
                        }
                    }
                    return true;
                }
                return false;
            });
        });

        // Register /limitedchunks command
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, environment) -> {
            dispatcher.register(Commands.literal("limitedchunks")
                    .requires(source -> LadsConfig.get().features.limitedChunkloading && source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                    .then(Commands.literal("listActiveTickets")
                            .then(Commands.argument("position", BlockPosArgument.blockPos())
                                    .executes(ctx -> {
                                        BlockPos pos = BlockPosArgument.getBlockPos(ctx, "position");
                                        ChunkPos chunkPos = ChunkPos.containing(pos);
                                        ServerLevel level = ctx.getSource().getLevel();
                                        boolean isForced = level.getChunkSource().getForceLoadedChunks().contains(chunkPos.pack());

                                        ctx.getSource().sendSystemMessage(Component.literal("[LimitedChunks] Chunk at " + pos.toShortString() +
                                                " (Chunk " + chunkPos.x() + ", " + chunkPos.z() + "): " +
                                                (isForced ? "FORCELOADED" : "Normal"))
                                                .withStyle(isForced ? ChatFormatting.YELLOW : ChatFormatting.GREEN));
                                        return 1;
                                    })))
                    .then(Commands.literal("listnonplayerchunktickets")
                            .executes(ctx -> {
                                ServerLevel level = ctx.getSource().getLevel();
                                LongSet forced = level.getChunkSource().getForceLoadedChunks();
                                ctx.getSource().sendSystemMessage(Component.literal("[LimitedChunks] " + forced.size() +
                                        " active forceloaded chunks in " + level.dimension().identifier())
                                        .withStyle(ChatFormatting.YELLOW));
                                return 1;
                            })));
        });
    }

    public static void onPlayerLeave(ServerPlayer player) {
        if (!LadsConfig.get().features.limitedChunkloading) return;
        long timeoutMs = (long) LadsConfig.get().features.chunkUnloadNoPlayerMinutes * 60L * 1000L;
        PENDING_UNLOADS.put(player.getUUID(), System.currentTimeMillis() + timeoutMs);
    }
}
