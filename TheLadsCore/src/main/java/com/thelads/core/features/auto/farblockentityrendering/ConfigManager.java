package com.thelads.core.features.auto.farblockentityrendering;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Recreation of FarBlockEntityRendering's ConfigManager (by IlyRac).
 * Provides the squared render distance consumed by the BlockEntityRenderer
 * overwrite. Floors the distance at the vanilla 64-block limit.
 */
public class ConfigManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("farblockentityrendering");

    private static SimpleConfig config;
    private static boolean initialized;

    public static void loadConfig() {
        try {
            config = SimpleConfig.load();
            initialized = true;
        } catch (Exception e) {
            LOGGER.error("Failed to load config, using defaults", e);
            config = new SimpleConfig();
            initialized = false;
        }
    }

    public static SimpleConfig getConfig() {
        if (!initialized) {
            loadConfig();
        }
        return config;
    }

    public static int getBlockEntityRenderDistance() {
        return Math.max(64, getConfig().getRenderDistanceBlocks());
    }

    public static double getBlockEntityRenderDistanceSquared() {
        int d = getBlockEntityRenderDistance();
        return (double) d * (double) d;
    }
}
