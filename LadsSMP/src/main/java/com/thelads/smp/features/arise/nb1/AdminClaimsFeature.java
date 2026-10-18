package com.thelads.smp.features.arise.nb1;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.server.permissions.Permissions;

/**
 * Native clean-room implementation of Admin Claims.
 * Protects operator-claimed chunk areas against griefing, block breaking, placing,
 * block interaction, and explosions.
 */
public final class AdminClaimsFeature {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP-AdminClaims");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CLAIMS_PATH = Path.of("config", "ladssmp", "admin_claims.json");
    private static final Set<String> CLAIMED_CHUNKS = Collections.synchronizedSet(new HashSet<>());

    public static void init() {
        loadClaims();

        // 1. Block breaking protection
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            if (!isEnabled() || level.isClientSide()) {
                return InteractionResult.PASS;
            }
            if (isChunkClaimed(level, pos) && !hasAdminPermission(player)) {
                sendDeniedFeedback(player);
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        // 2. Block interaction / placement protection
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (!isEnabled() || level.isClientSide()) {
                return InteractionResult.PASS;
            }
            if (isChunkClaimed(level, hitResult.getBlockPos()) && !hasAdminPermission(player)) {
                sendDeniedFeedback(player);
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        // 3. Command registration
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerCommands(dispatcher);
        });

        LOGGER.info("[Lads SMP] Admin Claims initialized ({} chunks currently claimed)", CLAIMED_CHUNKS.size());
    }

    public static boolean isEnabled() {
        return AriseNB1Config.get().adminClaims.enabled;
    }

    public static boolean hasAdminPermission(Player player) {
        return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    public static boolean isChunkClaimed(Level level, BlockPos pos) {
        if (!isEnabled()) return false;
        String key = formatKey(level.dimension().identifier().toString(), pos.getX() >> 4, pos.getZ() >> 4);
        return CLAIMED_CHUNKS.contains(key);
    }

    public static boolean isProtectedAgainstExplosion(Level level, BlockPos pos) {
        if (!isEnabled() || !AriseNB1Config.get().adminClaims.protectExplosions) {
            return false;
        }
        return isChunkClaimed(level, pos);
    }

    public static boolean claimChunk(Level level, ChunkPos pos) {
        String key = formatKey(level.dimension().identifier().toString(), pos.x(), pos.z());
        boolean added = CLAIMED_CHUNKS.add(key);
        if (added) saveClaims();
        return added;
    }

    public static boolean unclaimChunk(Level level, ChunkPos pos) {
        String key = formatKey(level.dimension().identifier().toString(), pos.x(), pos.z());
        boolean removed = CLAIMED_CHUNKS.remove(key);
        if (removed) saveClaims();
        return removed;
    }

    public static Set<String> getClaimedChunks() {
        return Collections.unmodifiableSet(CLAIMED_CHUNKS);
    }

    private static String formatKey(String dimension, int chunkX, int chunkZ) {
        return dimension + ":" + chunkX + "," + chunkZ;
    }

    private static void sendDeniedFeedback(Player player) {
        player.sendSystemMessage(
                Component.literal("This chunk is protected by an Admin Claim!").withStyle(ChatFormatting.RED)
        );
    }

    public static void loadClaims() {
        CLAIMED_CHUNKS.clear();
        if (!Files.exists(CLAIMS_PATH)) {
            saveClaims();
            return;
        }
        try (Reader reader = Files.newBufferedReader(CLAIMS_PATH)) {
            Set<String> loaded = GSON.fromJson(reader, new TypeToken<Set<String>>(){}.getType());
            if (loaded != null) {
                CLAIMED_CHUNKS.addAll(loaded);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load admin claims from {}", CLAIMS_PATH, e);
        }
    }

    public static void saveClaims() {
        try {
            if (CLAIMS_PATH.getParent() != null) {
                Files.createDirectories(CLAIMS_PATH.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(CLAIMS_PATH)) {
                GSON.toJson(CLAIMED_CHUNKS, writer);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save admin claims to {}", CLAIMS_PATH, e);
        }
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("adminclaim")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.literal("claim")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            ChunkPos pos = player.chunkPosition();
                            if (claimChunk(player.level(), pos)) {
                                ctx.getSource().sendSuccess(() -> Component.literal("Successfully claimed chunk [" + pos.x() + ", " + pos.z() + "] as an Admin Claim.")
                                        .withStyle(ChatFormatting.GREEN), true);
                            } else {
                                ctx.getSource().sendFailure(Component.literal("Chunk [" + pos.x() + ", " + pos.z() + "] is already claimed.")
                                        .withStyle(ChatFormatting.YELLOW));
                            }
                            return 1;
                        }))
                .then(Commands.literal("unclaim")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            ChunkPos pos = player.chunkPosition();
                            if (unclaimChunk(player.level(), pos)) {
                                ctx.getSource().sendSuccess(() -> Component.literal("Successfully unclaimed chunk [" + pos.x() + ", " + pos.z() + "].")
                                        .withStyle(ChatFormatting.GREEN), true);
                            } else {
                                ctx.getSource().sendFailure(Component.literal("Chunk [" + pos.x() + ", " + pos.z() + "] was not claimed.")
                                        .withStyle(ChatFormatting.YELLOW));
                            }
                            return 1;
                        }))
                .then(Commands.literal("list")
                        .executes(ctx -> {
                            String dim = ctx.getSource().getLevel().dimension().identifier().toString();
                            long count = CLAIMED_CHUNKS.stream().filter(s -> s.startsWith(dim + ":")).count();
                            ctx.getSource().sendSuccess(() -> Component.literal("Admin claims in dimension " + dim + ": " + count + " total chunks.")
                                    .withStyle(ChatFormatting.AQUA), false);
                            return 1;
                        }))
                .then(Commands.literal("reload")
                        .executes(ctx -> {
                            loadClaims();
                            ctx.getSource().sendSuccess(() -> Component.literal("Admin claims reloaded (" + CLAIMED_CHUNKS.size() + " total).")
                                    .withStyle(ChatFormatting.GREEN), true);
                            return 1;
                        }))
        );
    }
}
