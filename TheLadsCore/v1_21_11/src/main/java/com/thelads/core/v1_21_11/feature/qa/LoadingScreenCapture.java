package com.thelads.core.v1_21_11.feature.qa;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.LoadingOverlay;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * QA only (-Dthelads.verifyLoadingScreen): up to two completed frames of the startup loading screen, at least 0.25 s apart.
 * Mods' startup work blocks the render thread for most of the load, so a QA run usually yields one.
 */
public final class LoadingScreenCapture {
    private static final boolean ENABLED = Boolean.getBoolean("thelads.verifyLoadingScreen");
    private static long lastCapture;
    private static int taken;
    private LoadingScreenCapture() {}

    public static void frame(RenderTarget target) {
        Minecraft mc = Minecraft.getInstance();
        if (!ENABLED || taken >= 2 || !(mc.getOverlay() instanceof LoadingOverlay)) return;
        long now = System.nanoTime();
        if (taken > 0 && now - lastCapture < 250_000_000L) return;
        lastCapture = now;
        int index = ++taken;
        try {
            Path output = Files.createDirectories(mc.gameDirectory.toPath().resolve("screenshots")).resolve("lads-loading-" + index + ".png");
            Screenshot.takeScreenshot(target, image -> {
                try (image) {
                    image.writeToFile(output);
                    LoggerFactory.getLogger("TheLadsCore").info("Lads loading screen capture {}: {}", index, output);
                } catch (Exception failure) {
                    LoggerFactory.getLogger("TheLadsCore").error("Lads loading screen capture failed", failure);
                }
            });
        } catch (Exception failure) {
            LoggerFactory.getLogger("TheLadsCore").error("Lads loading screen capture failed", failure);
        }
    }
}
