/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 */
package com.thelads.core.features.farblockentityrendering;

import com.thelads.core.features.farblockentityrendering.FarBlockEntityRendering;
import com.thelads.core.features.farblockentityrendering.SimpleConfig;
import net.minecraft.client.Minecraft;

public class ConfigManager {
    private static SimpleConfig config;
    private static boolean initialized;

    public static void loadConfig() {
        try {
            config = SimpleConfig.load();
            initialized = true;
        }
        catch (Exception e) {
            FarBlockEntityRendering.LOGGER.error("Failed to load config, using defaults", (Throwable)e);
            config = new SimpleConfig();
            initialized = false;
        }
    }

    public static SimpleConfig getConfig() {
        if (!initialized) {
            ConfigManager.loadConfig();
        }
        return config;
    }

    public static int getBlockEntityRenderDistance() {
        return Math.max(64, ConfigManager.getConfig().getRenderDistanceBlocks());
    }

    public static double getBlockEntityRenderDistanceSquared() {
        int d = ConfigManager.getBlockEntityRenderDistance();
        return (double)d * (double)d;
    }

    public static void setRenderDistanceChunks(int chunks) {
        if (chunks < 4 || chunks > 32) {
            return;
        }
        ConfigManager.getConfig().renderDistanceChunks = chunks;
        ConfigManager.getConfig().save();
        ConfigManager.refreshRenderers();
        FarBlockEntityRendering.LOGGER.info("Block Entity render distance set to {} chunks ({} blocks)", (Object)chunks, (Object)(chunks * 16));
    }

    private static void refreshRenderers() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                mc.levelRenderer.resetLevelRenderData();
            }
        }
        catch (Exception e) {
            FarBlockEntityRendering.LOGGER.warn("Renderer refresh failed", (Throwable)e);
        }
    }
}

