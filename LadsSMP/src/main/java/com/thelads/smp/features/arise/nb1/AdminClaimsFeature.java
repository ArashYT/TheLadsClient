package com.thelads.smp.features.arise.nb1;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Native clean-room implementation of Admin Claims.
 * Protects operator-claimed chunk areas against griefing, block breaking, placing,
 * block/entity interaction, attacks, and explosions.
 * Persists claims into per-world data storage.
 */
public final class AdminClaimsFeature {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP-AdminClaims");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path GLOBAL_FALLBACK_PATH = Path.of("config", "ladssmp", "admin_claims.json");
    private static final Set<String> CLAIMED_CHUNKS = Collections.synchronizedSet(new HashSet<>());
    private static final Map<UUID, Long> FEEDBACK_COOLDOWNS = new ConcurrentHashMap<>();
    private static MinecraftServer currentServer = null;

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            currentServer = server;
            loadClaims(server);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            saveClaims(server);
            currentServer = null;
        });

        // 1. Block break event (hard cancellation)
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            if (!isEnabled() || level.isClientSide()) {
                return true;
            }
            if (isChunkClaimed(level, pos) && !hasAdminPermission(player)) {
                sendDeniedFeedback(player);
                return false;
            }
            return true;
        });

        // 2. Block punching protection
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

        // 3. Block interaction / placement protection (checks clicked block and placement offset across chunk borders)
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (!isEnabled() || level.isClientSide()) {
                return InteractionResult.PASS;
            }
            BlockPos targetPos = hitResult.getBlockPos();
            BlockPos placePos = targetPos.relative(hitResult.getDirection());
            if ((isChunkClaimed(level, targetPos) || isChunkClaimed(level, placePos)) && !hasAdminPermission(player)) {
                sendDeniedFeedback(player);
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        // 3b. Bucket / fluid use protection (A/7)
        net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, level, hand) -> {
            if (!isEnabled() || level.isClientSide()) return InteractionResult.PASS;
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof net.minecraft.world.item.BucketItem) {
                Vec3 start = player.getEyePosition();
                Vec3 view = player.getViewVector(1.0f);
                double reach = player.blockInteractionRange();
                Vec3 end = start.add(view.scale(reach));
                BlockHitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.SOURCE_ONLY, player));
                if (hit.getType() == HitResult.Type.BLOCK) {
                    BlockPos targetPos = hit.getBlockPos();
                    BlockPos placePos = targetPos.relative(hit.getDirection());
                    if ((isChunkClaimed(level, targetPos) || isChunkClaimed(level, placePos)) && !hasAdminPermission(player)) {
                        sendDeniedFeedback(player);
                        return InteractionResult.FAIL;
                    }
                }
            }
            return InteractionResult.PASS;
        });

        // 4. Entity interaction protection (armor stands, item frames, chests carts, etc.)
        UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (!isEnabled() || level.isClientSide()) {
                return InteractionResult.PASS;
            }
            if (isChunkClaimed(level, entity.blockPosition()) && !hasAdminPermission(player)) {
                sendDeniedFeedback(player);
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        // 5. Entity attack protection (hitting item frames, paintings, armor stands, etc.)
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (!isEnabled() || level.isClientSide()) {
                return InteractionResult.PASS;
            }
            if (isChunkClaimed(level, entity.blockPosition()) && !hasAdminPermission(player)) {
                sendDeniedFeedback(player);
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        // 6. Command registration
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerCommands(dispatcher);
        });

        LOGGER.info("[Lads SMP] Admin Claims initialized");
    }

    public static boolean isEnabled() {
        return AriseNB1Config.get().adminClaims.enabled;
    }

    public static boolean hasAdminPermission(Player player) {
        return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    public static boolean isChunkClaimed(Level level, BlockPos pos) {
        if (!isEnabled()) return false;
        String dimension = level != null ? level.dimension().identifier().toString() : "minecraft:overworld";
        String key = formatKey(dimension, pos.getX() >> 4, pos.getZ() >> 4);
        return CLAIMED_CHUNKS.contains(key);
    }

    /** True when pos is in an admin-claimed chunk and the player is not an admin (for features that change blocks). */
    public static boolean isProtectedFrom(Player player, Level level, BlockPos pos) {
        return isChunkClaimed(level, pos) && !hasAdminPermission(player);
    }

    public static boolean isProtected(Level level, BlockPos pos, Player player) {
        return isProtectedFrom(player, level, pos);
    }

    public static boolean isProtectedAgainstExplosion(Level level, BlockPos pos) {
        if (!isEnabled() || !AriseNB1Config.get().adminClaims.protectExplosions) {
            return false;
        }
        return isChunkClaimed(level, pos);
    }

    public static boolean claimChunk(Level level, ChunkPos pos) {
        String dimension = level != null ? level.dimension().identifier().toString() : "minecraft:overworld";
        String key = formatKey(dimension, pos.x(), pos.z());
        boolean added = CLAIMED_CHUNKS.add(key);
        if (added) {
            MinecraftServer server = level instanceof ServerLevel sl ? sl.getServer() : currentServer;
            saveClaims(server);
        }
        return added;
    }

    public static boolean unclaimChunk(Level level, ChunkPos pos) {
        String dimension = level != null ? level.dimension().identifier().toString() : "minecraft:overworld";
        String key = formatKey(dimension, pos.x(), pos.z());
        boolean removed = CLAIMED_CHUNKS.remove(key);
        if (removed) {
            MinecraftServer server = level instanceof ServerLevel sl ? sl.getServer() : currentServer;
            saveClaims(server);
        }
        return removed;
    }

    public static Set<String> getClaimedChunks() {
        return Collections.unmodifiableSet(CLAIMED_CHUNKS);
    }

    public static String formatKey(String dimension, int chunkX, int chunkZ) {
        return dimension + ":" + chunkX + "," + chunkZ;
    }

    public static void sendDeniedFeedback(Player player) {
        long now = System.currentTimeMillis();
        Long last = FEEDBACK_COOLDOWNS.get(player.getUUID());
        if (last != null && (now - last) < 1000) {
            return; // 1-second debounce to avoid audio/chat spam
        }
        FEEDBACK_COOLDOWNS.put(player.getUUID(), now);
        if (player instanceof ServerPlayer sp) {
            sp.sendSystemMessage(
                    Component.literal("This chunk is protected by an Admin Claim!").withStyle(ChatFormatting.RED),
                    true // Action bar notification
            );
        } else {
            player.sendSystemMessage(
                    Component.literal("This chunk is protected by an Admin Claim!").withStyle(ChatFormatting.RED)
            );
        }
    }

    private static Path getStoragePath(MinecraftServer server) {
        if (server != null) {
            try {
                Path dataDir = server.getWorldPath(LevelResource.DATA);
                return dataDir.resolve("ladssmp_admin_claims.json");
            } catch (Exception ignored) {}
        }
        return GLOBAL_FALLBACK_PATH;
    }

    public static void loadClaims(MinecraftServer server) {
        CLAIMED_CHUNKS.clear();
        Path path = getStoragePath(server);

        // Fallback migration: if world data does not exist yet, import from global config
        if (!Files.exists(path) && Files.exists(GLOBAL_FALLBACK_PATH)) {
            path = GLOBAL_FALLBACK_PATH;
        }

        if (!Files.exists(path)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            Set<String> loaded = GSON.fromJson(reader, new TypeToken<Set<String>>(){}.getType());
            if (loaded != null) {
                CLAIMED_CHUNKS.addAll(loaded);
            }
            LOGGER.info("[Lads SMP] Loaded {} admin claims from {}", CLAIMED_CHUNKS.size(), path);
        } catch (Exception e) {
            LOGGER.error("Failed to load admin claims from {}", path, e);
        }
    }

    public static void saveClaims(MinecraftServer server) {
        Path path = getStoragePath(server);
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(CLAIMED_CHUNKS, writer);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save admin claims to {}", path, e);
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
                            loadClaims(ctx.getSource().getServer());
                            ctx.getSource().sendSuccess(() -> Component.literal("Admin claims reloaded (" + CLAIMED_CHUNKS.size() + " total).")
                                    .withStyle(ChatFormatting.GREEN), true);
                            return 1;
                        }))
        );
    }
}
