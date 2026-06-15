package com.thelads.core.client.capes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Capes implements ClientModInitializer {
    public static final Capes INSTANCE = new Capes();
    public static final String MOD_ID = "capes";
    private static final Logger LOGGER = LoggerFactory.getLogger("Capes");
    public volatile CapeConfig CONFIG;

    public Logger getLOGGER() {
        return LOGGER;
    }

    public static CapeConfig getCONFIG() {
        if (INSTANCE.CONFIG == null) {
            synchronized (INSTANCE) {
                if (INSTANCE.CONFIG == null) {
                    INSTANCE.CONFIG = loadConfig();
                }
            }
        }
        return INSTANCE.CONFIG;
    }

    public static Identifier identifier(String id) {
        return Identifier.fromNamespaceAndPath(MOD_ID, id);
    }

    @Override
    public void onInitializeClient() {
        getCONFIG();
        ClientCommandRegistrationCallback.EVENT.register(Capes::registerCommand);
    }

    private static Component getDebugInfoForPlayer(GameProfile profile) {
        PlayerHandler handler = PlayerHandler.fromProfile(profile);
        CapeType capeType = handler.getCapeType();
        MutableComponent infoText = Component.empty()
            .append("Name: " + profile.name() + "\n")
            .append("UUID: " + profile.id() + "\n")
            .append("Type: " + handler.getCapeType() + "\n")
            .append("IsAnimated: " + handler.getHasAnimatedCape() + "\n")
            .append("HasElytraTexture: " + handler.getHasElytraTexture() + "\n")
            .append("URL: " + (capeType != null ? capeType.getURL(profile) : "null"));

        MutableComponent text = Component.literal("Click here to copy debug info for player " + profile.name() + ".");
        ClickEvent.CopyToClipboard clickEvent = new ClickEvent.CopyToClipboard(infoText.getString());
        HoverEvent.ShowText hoverEvent = new HoverEvent.ShowText(infoText);
        Style style = Style.EMPTY
            .withClickEvent(clickEvent)
            .withHoverEvent(hoverEvent)
            .withColor(ChatFormatting.BLUE)
            .withUnderlined(true);
        text.setStyle(style);
        return text;
    }

    private static CapeConfig loadConfig() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        File configFile = new File(FabricLoader.getInstance().getConfigDir().toFile(), "capes.json5");
        CapeConfig finalConfig;
        LOGGER.info("Trying to read config file...");
        try {
            if (configFile.createNewFile()) {
                LOGGER.info("No config file found, creating a new one...");
                String json = gson.toJson(JsonParser.parseString(gson.toJson(new CapeConfig())));
                try (PrintWriter out = new PrintWriter(configFile)) {
                    out.println(json);
                }
                finalConfig = new CapeConfig();
                LOGGER.info("Successfully created default config file.");
            } else {
                LOGGER.info("A config file was found, loading it..");
                byte[] bytes = Files.readAllBytes(configFile.toPath());
                finalConfig = gson.fromJson(new String(bytes, StandardCharsets.UTF_8), CapeConfig.class);
                LOGGER.info("Successfully loaded config file.");
            }
        } catch (Exception exception) {
            LOGGER.error("There was an error creating/loading the config file!", exception);
            finalConfig = new CapeConfig();
            LOGGER.warn("Defaulting to original config.");
        }
        if (finalConfig.getClientCapeType() == null) {
            finalConfig.setClientCapeType(CapeType.MINECRAFT);
        }
        return finalConfig;
    }

    private static void registerCommand(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext registryAccess) {
        dispatcher.register(
            ClientCommands.literal(MOD_ID)
                .then(ClientCommands.literal("debug")
                    .then(ClientCommands.argument("target", StringArgumentType.string())
                        .executes(Capes::executeDebugTarget)
                    )
                    .executes(Capes::executeDebugSelf)
                )
        );
    }

    private static int executeDebugTarget(CommandContext<FabricClientCommandSource> context) throws CommandSyntaxException {
        FabricClientCommandSource source = context.getSource();
        String targetName = StringArgumentType.getString(context, "target");

        AbstractClientPlayer target = source.getLevel().players().stream()
            .filter(player -> player.getGameProfile().name().equals(targetName))
            .findFirst()
            .orElse(null);

        if (target == null) {
            throw EntityArgument.NO_PLAYERS_FOUND.create();
        }

        GameProfile gameProfile = target.getGameProfile();
        Component debugInfo = getDebugInfoForPlayer(gameProfile);
        source.getPlayer().sendSystemMessage(debugInfo);
        return 1;
    }

    private static int executeDebugSelf(CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();
        GameProfile gameProfile = source.getPlayer().getGameProfile();
        Component debugInfo = getDebugInfoForPlayer(gameProfile);
        source.getPlayer().sendSystemMessage(debugInfo);
        return 1;
    }
}
