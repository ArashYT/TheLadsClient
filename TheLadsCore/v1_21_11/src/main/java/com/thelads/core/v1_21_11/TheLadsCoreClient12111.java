package com.thelads.core.v1_21_11;

import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.v1_21_11.adapter.VanillaGameBridge12111;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TheLadsCoreClient12111 implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore-1.21.11");

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing TheLadsCore for Minecraft 1.21.11...");
        LadsGameBridge.set(new VanillaGameBridge12111());
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.BEFORE_INIT.register((client, screen, width, height) -> {
            // A failed or cancelled server transfer leaves the old server's packs loaded: vanilla keeps them for the next server.
            if (client.level != null || client.getConnection() != null) return;
            if (screen instanceof net.minecraft.client.gui.screens.DisconnectedScreen
                    || screen instanceof net.minecraft.client.gui.screens.TitleScreen
                    || screen instanceof net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen) {
                LadsGameBridge.get().clearServerResourcePacks();
            }
        });
        com.thelads.core.security.AntiInjectDetector.init();
        ConfigManager.load();
        com.thelads.core.config.ModuleSupport.registerBuiltIn("Threads");
        ModuleSupport.registerBuiltIn(com.thelads.core.client.ServerListDrag.NAME);
        // These HUD controls are consumed by HudManager through the required native HUD mixin.
        // Other modules remain unavailable until their native behavior and options are connected.
        ModuleSupport.registerBuiltIn("FPS", "Coordinates", "PingHUD", "Memory", "Speed",
            "Day", "Time", "XP", "Potion Effects", "Zoom", com.thelads.core.modules.ToggleSprintModule.NAME,
            "Fullbright", "TitleScreen", "CPS", "Keystrokes",
            "Biome", "ArmorHUD", "Direction", "Health", "Hunger", "Scoreboard", "TexturePacks");
        com.thelads.core.v1_21_11.gui.ExternalModSettings.register();
        com.thelads.core.v1_21_11.feature.NativeQualityOfLife.register();
        com.thelads.core.v1_21_11.feature.NativeHypixel.initialize();
        com.thelads.core.v1_21_11.feature.NativeLadsApi.initialize();
        com.thelads.core.v1_21_11.feature.AddServerProbe.register();
        com.thelads.core.v1_21_11.feature.QaProbe12111.register();
        com.thelads.core.v1_21_11.embedded.EmbeddedMods.clientInit();
        LOGGER.info("TheLadsCore 1.21.11 initialized successfully.");
    }
}
