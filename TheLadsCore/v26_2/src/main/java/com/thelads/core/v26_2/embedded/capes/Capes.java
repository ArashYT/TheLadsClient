// Ported from Capes 1.5.11+26.2 by Cael (LGPL-2.1-only) from Kotlin to Java; modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.capes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.thelads.core.v26_2.embedded.capes.handler.PlayerHandler;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Objects;

public final class Capes {

    public static final String MOD_ID = "capes";

    public static final Logger LOGGER = LoggerFactory.getLogger("Capes");

    private Capes() {
    }

    /** Kotlin's {@code val CONFIG by lazy}: loaded on first use. */
    public static CapeConfig getConfig() {
        return ConfigHolder.CONFIG;
    }

    private static final class ConfigHolder {
        static final CapeConfig CONFIG = loadConfig();
    }

    private static CapeConfig loadConfig() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        File configFile = new File(FabricLoader.getInstance().getConfigDir() + File.separator + "capes.json5");
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
                finalConfig = gson.fromJson(new String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8), CapeConfig.class);
                if (finalConfig == null) {
                    throw new NullPointerException("The config file was empty.");
                } else {
                    LOGGER.info("Successfully loaded config file.");
                }
            }
        } catch (Exception exception) {
            LOGGER.error("There was an error creating/loading the config file!", exception);
            finalConfig = new CapeConfig();
            LOGGER.warn("Defaulting to original config.");
        }
        if (finalConfig.getClientCapeType() == null) finalConfig.setClientCapeType(CapeType.MINECRAFT);
        return finalConfig;
    }

    public static Identifier identifier(String id) {
        return Identifier.fromNamespaceAndPath(MOD_ID, id);
    }

    /** Upstream onInitializeClient. */
    public static void init() {
        getConfig();
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommands.literal("capes")
                        .then(ClientCommands.literal("debug")
                                .then(ClientCommands.argument("target", StringArgumentType.string())
                                        .executes(context -> {
                                            AbstractClientPlayer target = context.getSource().getLevel().players().stream()
                                                    .filter(it -> Objects.equals(it.getGameProfile().name(), StringArgumentType.getString(context, "target")))
                                                    .findFirst()
                                                    .orElseThrow(EntityArgument.NO_PLAYERS_FOUND::create);
                                            Component debugInfo = getDebugInfoForPlayer(target.getGameProfile());
                                            context.getSource().getPlayer().sendSystemMessage(debugInfo);
                                            return 1;
                                        })
                                )
                                .executes(context -> {
                                    Component debugInfo = getDebugInfoForPlayer(context.getSource().getPlayer().getGameProfile());
                                    context.getSource().getPlayer().sendSystemMessage(debugInfo);
                                    return 1;
                                })
                        )
        ));
    }

    private static Component getDebugInfoForPlayer(GameProfile profile) {
        PlayerHandler handler = PlayerHandler.fromProfile(profile);
        CapeType capeType = handler.getCapeType();

        MutableComponent infoText = Component.empty()
                .append("Name: " + profile.name() + "\n")
                .append("UUID: " + profile.id() + "\n")
                .append("Type: " + capeType + "\n")
                .append("IsAnimated: " + handler.getHasAnimatedCape() + "\n")
                .append("HasElytraTexture: " + handler.getHasElytraTexture() + "\n")
                .append("URL: " + (capeType != null ? capeType.getURL(profile) : null));

        MutableComponent text = Component.literal("Click here to copy debug info for player " + profile.name() + ".");
        ClickEvent clickEvent = new ClickEvent.CopyToClipboard(infoText.getString());
        HoverEvent hoverEvent = new HoverEvent.ShowText(infoText);
        Style style = Style.EMPTY
                .withClickEvent(clickEvent)
                .withHoverEvent(hoverEvent)
                .withColor(ChatFormatting.BLUE)
                .withUnderlined(true);
        text.setStyle(style);

        return text;
    }

}
