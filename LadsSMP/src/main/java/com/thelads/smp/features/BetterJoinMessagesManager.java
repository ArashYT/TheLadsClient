package com.thelads.smp.features;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.thelads.smp.config.LadsConfig;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Consolidates 'BetterJoinMessages': Custom join, leave, and first-join messages with color codes,
 * distance-based or global broadcast, and the /bjm command.
 */
public final class BetterJoinMessagesManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP/BetterJoinMessages");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Type SEEN_SET_TYPE = new TypeToken<Set<String>>() {}.getType();

    private static volatile BjmConfig CONFIG = new BjmConfig();
    private static final Set<String> SEEN_PLAYERS = Collections.synchronizedSet(new HashSet<>());

    public static final class BjmConfig {
        public String joinMessage = "&e{player} joined the game";
        public String leaveMessage = "&e{player} left the game";
        public String firstJoinMessage = "&e{player} joined the game for the first time!";
        public boolean firstJoinEnabled = true;
        public boolean sendGlobal = true;
        public double distance = 50.0;
    }

    private BetterJoinMessagesManager() {}

    public static void init() {
        loadConfig();
        loadSeenPlayers();

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (!LadsConfig.get().features.betterJoinMessages || handler.player == null) return;
            ServerPlayer player = handler.player;
            String uuid = player.getUUID().toString();
            boolean firstJoin = SEEN_PLAYERS.add(uuid);
            if (firstJoin) {
                saveSeenPlayers();
            }
            BjmConfig cfg = CONFIG;
            String template = (firstJoin && cfg.firstJoinEnabled) ? cfg.firstJoinMessage : cfg.joinMessage;
            if (template != null && !template.isBlank()) {
                Component msg = formatMessage(template, player.getGameProfile().name());
                broadcastMessage(server, player, msg);
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (!LadsConfig.get().features.betterJoinMessages || handler.player == null) return;
            ServerPlayer player = handler.player;
            String template = CONFIG.leaveMessage;
            if (template != null && !template.isBlank()) {
                Component msg = formatMessage(template, player.getGameProfile().name());
                broadcastMessage(server, player, msg);
            }
        });

        CommandRegistrationCallback.EVENT.register(BetterJoinMessagesManager::registerCommand);
    }

    public static BjmConfig getConfig() {
        return CONFIG;
    }

    public static Path getConfigPath() {
        try {
            return FabricLoader.getInstance().getConfigDir().resolve("betterjoinmessages.json");
        } catch (Throwable ignored) {
            return Path.of("build", "test-config", "betterjoinmessages.json");
        }
    }

    public static Path getSeenPlayersPath() {
        try {
            return FabricLoader.getInstance().getConfigDir().resolve("betterjoinmessages_seen_players.json");
        } catch (Throwable ignored) {
            return Path.of("build", "test-config", "betterjoinmessages_seen_players.json");
        }
    }

    public static synchronized void loadConfig() {
        Path path = getConfigPath();
        try {
            if (Files.exists(path)) {
                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    BjmConfig loaded = GSON.fromJson(reader, BjmConfig.class);
                    if (loaded != null) {
                        CONFIG = loaded;
                        return;
                    }
                }
            }
            CONFIG = new BjmConfig();
            saveConfig();
        } catch (Exception e) {
            LOGGER.warn("Failed to load betterjoinmessages.json, using defaults", e);
        }
    }

    public static synchronized void saveConfig() {
        Path path = getConfigPath();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(CONFIG, writer);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to save betterjoinmessages.json", e);
        }
    }

    public static synchronized void loadSeenPlayers() {
        Path path = getSeenPlayersPath();
        try {
            if (Files.exists(path)) {
                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    Set<String> loaded = GSON.fromJson(reader, SEEN_SET_TYPE);
                    if (loaded != null) {
                        SEEN_PLAYERS.clear();
                        SEEN_PLAYERS.addAll(loaded);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to load betterjoinmessages_seen_players.json", e);
        }
    }

    public static synchronized void saveSeenPlayers() {
        Path path = getSeenPlayersPath();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                synchronized (SEEN_PLAYERS) {
                    GSON.toJson(SEEN_PLAYERS, SEEN_SET_TYPE, writer);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to save betterjoinmessages_seen_players.json", e);
        }
    }

    public static Component formatMessage(String template, String playerName) {
        if (template == null) return Component.empty();
        String text = template.replace("{player}", playerName != null ? playerName : "");
        MutableComponent root = Component.empty();
        StringBuilder currentSegment = new StringBuilder();
        Style currentStyle = Style.EMPTY;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c == '&' || c == '\u00A7') && i + 1 < text.length()) {
                ChatFormatting fmt = ChatFormatting.getByCode(Character.toLowerCase(text.charAt(i + 1)));
                if (fmt != null) {
                    if (!currentSegment.isEmpty()) {
                        root.append(Component.literal(currentSegment.toString()).withStyle(currentStyle));
                        currentSegment.setLength(0);
                    }
                    if (fmt == ChatFormatting.RESET) {
                        currentStyle = Style.EMPTY;
                    } else {
                        currentStyle = currentStyle.applyFormat(fmt);
                    }
                    i++;
                    continue;
                }
            }
            currentSegment.append(c);
        }
        if (!currentSegment.isEmpty()) {
            root.append(Component.literal(currentSegment.toString()).withStyle(currentStyle));
        }
        return root;
    }

    private static void broadcastMessage(MinecraftServer server, ServerPlayer subject, Component msg) {
        BjmConfig cfg = CONFIG;
        if (cfg.sendGlobal) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                p.sendSystemMessage(msg);
            }
        } else {
            double maxDistSq = cfg.distance * cfg.distance;
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (p.level() == subject.level() && p.distanceToSqr(subject) <= maxDistSq) {
                    p.sendSystemMessage(msg);
                }
            }
        }
    }

    private static void registerCommand(
            CommandDispatcher<CommandSourceStack> dispatcher,
            CommandBuildContext registryAccess,
            Commands.CommandSelection environment) {
        dispatcher.register(Commands.literal("bjm")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.literal("join")
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    CONFIG.joinMessage = StringArgumentType.getString(ctx, "message");
                                    saveConfig();
                                    ctx.getSource().sendSuccess(() -> Component.literal("Updated join message.").withStyle(ChatFormatting.GREEN), true);
                                    return 1;
                                })))
                .then(Commands.literal("leave")
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    CONFIG.leaveMessage = StringArgumentType.getString(ctx, "message");
                                    saveConfig();
                                    ctx.getSource().sendSuccess(() -> Component.literal("Updated leave message.").withStyle(ChatFormatting.GREEN), true);
                                    return 1;
                                })))
                .then(Commands.literal("firstjoin")
                        .then(Commands.literal("enabled")
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            CONFIG.firstJoinEnabled = BoolArgumentType.getBool(ctx, "enabled");
                                            saveConfig();
                                            ctx.getSource().sendSuccess(() -> Component.literal("Updated first join enabled: " + CONFIG.firstJoinEnabled).withStyle(ChatFormatting.GREEN), true);
                                            return 1;
                                        })))
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    CONFIG.firstJoinMessage = StringArgumentType.getString(ctx, "message");
                                    saveConfig();
                                    ctx.getSource().sendSuccess(() -> Component.literal("Updated first join message.").withStyle(ChatFormatting.GREEN), true);
                                    return 1;
                                })))
                .then(Commands.literal("global")
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    CONFIG.sendGlobal = BoolArgumentType.getBool(ctx, "enabled");
                                    saveConfig();
                                    ctx.getSource().sendSuccess(() -> Component.literal("Updated sendGlobal: " + CONFIG.sendGlobal).withStyle(ChatFormatting.GREEN), true);
                                    return 1;
                                })))
                .then(Commands.literal("distance")
                        .then(Commands.argument("distance", DoubleArgumentType.doubleArg(1.0))
                                .executes(ctx -> {
                                    CONFIG.distance = DoubleArgumentType.getDouble(ctx, "distance");
                                    saveConfig();
                                    ctx.getSource().sendSuccess(() -> Component.literal("Updated distance: " + CONFIG.distance).withStyle(ChatFormatting.GREEN), true);
                                    return 1;
                                })))
                .then(Commands.literal("reload")
                        .executes(ctx -> {
                            loadConfig();
                            loadSeenPlayers();
                            ctx.getSource().sendSuccess(() -> Component.literal("Reloaded BetterJoinMessages config.").withStyle(ChatFormatting.GREEN), true);
                            return 1;
                        })));
    }
}
