package com.thelads.smp.features.arise.nb1;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Native clean-room implementation of Dimension Access Manager.
 * Restricts player entry into designated dimensions (Nether, End, modded dimensions)
 * via operator commands and persistent permission storage.
 */
public final class DimensionAccessFeature {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP-DimensionAccess");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path getDefaultAccessPath() {
        try {
            return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("ladssmp").resolve("dimension_access.json");
        } catch (Throwable ignored) {
            return Path.of("build", "test-config", "ladssmp", "dimension_access.json");
        }
    }
    private static final Path ACCESS_PATH = getDefaultAccessPath();

    // Key: player UUID or lowercase username -> Set of denied dimension IDs (e.g. "minecraft:the_nether")
    private static final Map<String, Set<String>> DENIED_DIMENSIONS = new ConcurrentHashMap<>();

    public static void init() {
        loadData();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerCommands(dispatcher);
        });

        LOGGER.info("[Lads SMP] Dimension Access Manager initialized ({} player restriction profiles loaded)", DENIED_DIMENSIONS.size());
    }

    public static boolean isEnabled() {
        return AriseNB1Config.get().dimensionAccess.enabled;
    }

    public static boolean isDimensionDenied(ServerPlayer player, String dimensionId) {
        if (!isEnabled()) return false;

        String name = player.getName().getString().toLowerCase(Locale.ROOT);
        String uuid = player.getUUID().toString();

        Set<String> denied = DENIED_DIMENSIONS.get(name);
        if (denied == null) {
            denied = DENIED_DIMENSIONS.get(uuid);
        }

        if (denied != null) {
            return denied.contains(dimensionId) || denied.contains(dimensionId.replace("minecraft:", ""));
        }
        return false;
    }

    public static boolean denyAccess(String target, String dimensionId) {
        String key = target.toLowerCase(Locale.ROOT);
        DENIED_DIMENSIONS.computeIfAbsent(key, k -> Collections.synchronizedSet(new HashSet<>())).add(dimensionId);
        saveData();
        return true;
    }

    public static boolean allowAccess(String target, String dimensionId) {
        String key = target.toLowerCase(Locale.ROOT);
        Set<String> denied = DENIED_DIMENSIONS.get(key);
        if (denied != null) {
            boolean removed = denied.remove(dimensionId) || denied.remove(dimensionId.replace("minecraft:", ""));
            if (denied.isEmpty()) {
                DENIED_DIMENSIONS.remove(key);
            }
            saveData();
            return removed;
        }
        return false;
    }

    public static Set<String> getDeniedDimensions(String target) {
        String key = target.toLowerCase(Locale.ROOT);
        Set<String> set = DENIED_DIMENSIONS.get(key);
        return set != null ? Collections.unmodifiableSet(set) : Collections.emptySet();
    }

    public static void sendDeniedFeedback(ServerPlayer player, String dimensionId) {
        player.sendSystemMessage(
                Component.literal("Access to dimension '" + dimensionId + "' is restricted by server administration.")
                        .withStyle(ChatFormatting.RED)
        );
    }

    public static void loadData() {
        if (!Files.exists(ACCESS_PATH)) {
            DENIED_DIMENSIONS.clear();
            saveData();
            return;
        }
        try (Reader reader = Files.newBufferedReader(ACCESS_PATH)) {
            Map<String, Set<String>> loaded = GSON.fromJson(reader, new TypeToken<Map<String, Set<String>>>(){}.getType());
            if (loaded != null) {
                DENIED_DIMENSIONS.clear();
                loaded.forEach((k, v) -> DENIED_DIMENSIONS.put(k.toLowerCase(Locale.ROOT), Collections.synchronizedSet(new HashSet<>(v))));
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load dimension access rules from {}", ACCESS_PATH, e);
            try {
                Path corrupt = ACCESS_PATH.resolveSibling(ACCESS_PATH.getFileName().toString() + ".corrupt-" + System.currentTimeMillis());
                Files.copy(ACCESS_PATH, corrupt, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.warn("Corrupt dimension access file backed up to {}", corrupt);
            } catch (Exception ignored) {}
        }
    }

    public static void saveData() {
        try {
            if (ACCESS_PATH.getParent() != null) {
                Files.createDirectories(ACCESS_PATH.getParent());
            }
            Path temp = ACCESS_PATH.resolveSibling(ACCESS_PATH.getFileName().toString() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temp)) {
                GSON.toJson(DENIED_DIMENSIONS, writer);
            }
            Files.move(temp, ACCESS_PATH, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            LOGGER.error("Failed to save dimension access rules to {}", ACCESS_PATH, e);
        }
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("dimension")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.literal("access")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.literal("allow")
                                        .then(Commands.argument("dimension", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                                    String dim = StringArgumentType.getString(ctx, "dimension");
                                                    allowAccess(target.getName().getString(), dim);
                                                    ctx.getSource().sendSuccess(() -> Component.literal("Allowed " + target.getName().getString() + " access to " + dim)
                                                            .withStyle(ChatFormatting.GREEN), true);
                                                    return 1;
                                                })))
                                .then(Commands.literal("deny")
                                        .then(Commands.argument("dimension", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                                    String dim = StringArgumentType.getString(ctx, "dimension");
                                                    denyAccess(target.getName().getString(), dim);
                                                    ctx.getSource().sendSuccess(() -> Component.literal("Denied " + target.getName().getString() + " access to " + dim)
                                                            .withStyle(ChatFormatting.RED), true);
                                                    return 1;
                                                })))
                                .then(Commands.literal("list")
                                        .executes(ctx -> {
                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                            Set<String> denied = getDeniedDimensions(target.getName().getString());
                                            ctx.getSource().sendSuccess(() -> Component.literal("Restricted dimensions for " + target.getName().getString() + ": " + (denied.isEmpty() ? "None" : String.join(", ", denied)))
                                                    .withStyle(ChatFormatting.AQUA), false);
                                            return 1;
                                        })))
                        .then(Commands.literal("reload")
                                .executes(ctx -> {
                                    loadData();
                                    ctx.getSource().sendSuccess(() -> Component.literal("Dimension access rules reloaded.")
                                            .withStyle(ChatFormatting.GREEN), true);
                                    return 1;
                                }))
                )
        );
    }
}
