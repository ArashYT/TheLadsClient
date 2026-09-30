package com.thelads.core.v26_2.feature;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.LoggerFactory;

/** Verifies the actual device after a world has reached the renderer, never just its saved preference. */
public final class Renderer134Probe {
    private static boolean done;
    public static void register() {
        if (!Boolean.getBoolean("thelads.verify134")) return;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (done || client.player == null || client.level == null) return;
            done = true;
            try {
            NativeWorldVerification.checkedGameDirectory(client.gameDirectory.toPath());
            String device = RenderSystem.getDevice().getClass().getName();
            String description = RenderSystem.getDevice().getDeviceInfo().backendName();
            String expected = System.getProperty("thelads.verifyRenderer", "vulkan");
            boolean vulkan = (device + " " + description).toLowerCase(java.util.Locale.ROOT).contains("vulkan");
            if (vulkan != expected.equalsIgnoreCase("vulkan"))
                throw new IllegalStateException("Expected " + expected + " renderer, got " + device + " / " + description);
            if (!FabricLoader.getInstance().isModLoaded("flashback")) throw new IllegalStateException("Flashback is not loaded");
            LoggerFactory.getLogger("TheLadsCore").info("Lads 1.3.4 renderer probe END: 2 passed, 0 failed; expected={}, device={}, backend={}, Flashback={}",
                expected, device, description, FabricLoader.getInstance().getModContainer("flashback").orElseThrow().getMetadata().getVersion());
            } catch (Exception failure) {
                LoggerFactory.getLogger("TheLadsCore").error("Lads 1.3.4 renderer probe FAILED", failure);
            }
        });
    }
}
