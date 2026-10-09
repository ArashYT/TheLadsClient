package com.thelads.core.v1_20_1.gui;

import com.mojang.blaze3d.platform.NativeImage;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import org.slf4j.LoggerFactory;

/**
 * QA only (-Dthelads.verifyLoadingScreen, set by the harness): up to two completed frames of the startup loading screen, at least
 * 0.25 s apart, saved as screenshots/lads-loading-N.png like 26.3's capture. Called right after the Lads loading screen drew.
 */
final class LoadingQa1201 {
    private static final boolean ENABLED = Boolean.getBoolean("thelads.verifyLoadingScreen");
    private static long lastCapture;
    private static int taken;

    private LoadingQa1201() {}

    static void frame() {
        if (!ENABLED || taken >= 2) return;
        long now = System.nanoTime();
        if (taken > 0 && now - lastCapture < 250_000_000L) return;
        lastCapture = now;
        int index = ++taken;
        try {
            Minecraft mc = Minecraft.getInstance();
            Path output = Files.createDirectories(mc.gameDirectory.toPath().resolve("screenshots")).resolve("lads-loading-" + index + ".png");
            NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget());
            Util.ioPool().execute(() -> {
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
