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
        ConfigManager.load();
        // These HUD controls are consumed by HudManager through the required native HUD mixin.
        // Other modules remain unavailable until their native behavior and options are connected.
        ModuleSupport.registerBuiltIn("FPS", "Coordinates", "PingHUD", "Memory", "Speed",
            "Day", "Time", "XP", "Potion Effects", "Zoom", "ToggleSprint", "ToggleSneak",
            "Fullbright", "TitleScreen", "Title Scale", "CPS", "Keystrokes",
            "Biome", "ArmorHUD", "Direction", "Health", "Hunger", "Scoreboard", "TexturePacks");
        com.thelads.core.v1_21_11.gui.ExternalModSettings.register();
        LOGGER.info("TheLadsCore 1.21.11 initialized successfully.");
    }
}
