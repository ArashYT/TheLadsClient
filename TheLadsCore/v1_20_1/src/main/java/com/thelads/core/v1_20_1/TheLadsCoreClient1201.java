package com.thelads.core.v1_20_1;

import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.v1_20_1.adapter.VanillaGameBridge1201;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TheLadsCoreClient1201 implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore-1.20.1");

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing TheLadsCore for Minecraft 1.20.1...");
        LadsGameBridge.set(new VanillaGameBridge1201());
        ConfigManager.load();
        ModuleSupport.registerBuiltIn("Threads");
        ModuleSupport.registerBuiltInRestart("Jasione");
        ModuleSupport.registerBuiltIn("FPS", "Coordinates", "PingHUD", "Memory", "Speed",
            "Day", "Time", "XP", "Potion Effects", "Zoom", com.thelads.core.modules.ToggleSprintModule.NAME,
            "Fullbright", "TitleScreen", "Title Scale", "CPS", "Keystrokes",
            "Biome", "ArmorHUD", "Direction", "Health", "Hunger", "Scoreboard", "TexturePacks");

        LOGGER.info("TheLadsCore 1.20.1 initialized successfully.");
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            com.thelads.core.mods.CoreCatalogExporter.exportIfChanged();
        });
    }
}
