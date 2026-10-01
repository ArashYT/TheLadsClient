// Ported from Capes 1.5.4+1.21 by Cael (LGPL-2.1-only) from Kotlin to Java (Capes.kt, fabric/FabricCapes.kt and the Fabric Platform glue); modified by The Lads: remapped to Mojang mappings and repackaged into Lads Core.
package com.thelads.core.v1_21_1.embedded.capes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.thelads.core.v1_21_1.embedded.capes.handler.PlayerHandler;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
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
        return finalConfig;
    }

    public static ResourceLocation identifier(String id) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, id);
    }

    /** Upstream FabricCapes.onInitializeClient. */
    public static void init() {
        getConfig();
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal("capes")
                        .then(ClientCommandManager.literal("debug")
                                .then(ClientCommandManager.argument("target", StringArgumentType.string())
                                        .executes(context -> {
                                            AbstractClientPlayer target = context.getSource().getWorld().players().stream()
                                                    .filter(it -> Objects.equals(it.getGameProfile().getName(), StringArgumentType.getString(context, "target")))
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
                .append("Name: " + profile.getName() + "\n")
                .append("UUID: " + profile.getId() + "\n")
                .append("Type: " + capeType + "\n")
                .append("IsAnimated: " + handler.getHasAnimatedCape() + "\n")
                .append("HasElytraTexture: " + handler.getHasElytraTexture() + "\n")
                .append("URL: " + (capeType != null ? capeType.getURL(profile) : null));

        MutableComponent text = Component.literal("Click here to copy debug info for player " + profile.getName() + ".");
        ClickEvent clickEvent = new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, infoText.getString());
        HoverEvent hoverEvent = new HoverEvent(HoverEvent.Action.SHOW_TEXT, infoText);
        Style style = Style.EMPTY
                .withClickEvent(clickEvent)
                .withHoverEvent(hoverEvent)
                .withColor(ChatFormatting.BLUE)
                .withUnderlined(true);
        text.setStyle(style);

        return text;
    }
}
