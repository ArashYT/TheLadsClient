package com.thelads.core.v1_21_1;

import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.v1_21_1.adapter.VanillaGameBridge121;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TheLadsCoreClient121 implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore-1.21.1");

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing TheLadsCore for Minecraft 1.21.1...");
        LadsGameBridge.set(new VanillaGameBridge121());
        ConfigManager.load();
        com.thelads.core.config.ModuleSupport.registerBuiltIn("Threads");
        // GuiMixin draws these through HudManager with VanillaGameBridge121 data, so settings and the HUD editor can switch them.
        com.thelads.core.config.ModuleSupport.registerBuiltIn("FPS", "Coordinates", "PingHUD", "Memory", "Speed", "Day", "Time", "XP",
            "Potion Effects", "CPS", "Keystrokes", "Biome", "Direction", "Health", "Hunger", "TexturePacks");
        // Nothing registers later on 1.21.1 (no tick hook), so the launcher catalog is written once here.
        com.thelads.core.mods.CoreCatalogExporter.exportIfChanged();
        LOGGER.info("TheLadsCore 1.21.1 initialized successfully.");
    }
}
