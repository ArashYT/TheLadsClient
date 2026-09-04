package com.thelads.core.v26_2;

import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.v26_2.adapter.VanillaGameBridge26;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TheLadsCoreClient26 implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore-26.2");

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing TheLadsCore for Minecraft 26.2...");
        LadsGameBridge.set(new VanillaGameBridge26());
        ConfigManager.load();
        LOGGER.info("TheLadsCore 26.2 initialized successfully.");
    }
}
