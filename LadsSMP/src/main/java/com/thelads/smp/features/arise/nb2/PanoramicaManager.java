package com.thelads.smp.features.arise.nb2;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles capturing 360-degree 6-sided cubemap panoramic screenshot sets
 * for custom title screen backgrounds (Panoramica).
 */
public final class PanoramicaManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP/Panoramica");

    private PanoramicaManager() {}

    public static boolean isPanoramicaEnabled() {
        return NB2Config.get().panoramica;
    }

    @Environment(EnvType.CLIENT)
    public static void capturePanorama() {
        if (!isPanoramicaEnabled()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }

        try {
            Component result = mc.grabPanoramixScreenshot(mc.gameDirectory);
            if (result != null) {
                mc.player.sendSystemMessage(Component.literal("§a[Panoramica] ").append(result));
            } else {
                mc.player.sendSystemMessage(Component.literal("§a[Panoramica] Panorama captured to screenshots folder!"));
            }
        } catch (Exception e) {
            LOGGER.error("Failed during panorama capture", e);
            mc.player.sendSystemMessage(Component.literal("§c[Panoramica] Error capturing panorama: " + e.getMessage()));
        }
    }
}
