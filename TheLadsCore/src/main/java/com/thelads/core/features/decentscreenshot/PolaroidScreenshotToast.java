package com.thelads.core.features.decentscreenshot;

import com.thelads.core.features.decentscreenshot.ScreenshotTextureCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import java.io.File;

public class PolaroidScreenshotToast implements Toast {
    private final File screenshotFile;
    private ScreenshotTextureCache.ScreenshotTexture texture;
    private Visibility visibility = Visibility.SHOW;
    private long firstRenderTime = -1L;

    public PolaroidScreenshotToast(File screenshotFile) {
        this.screenshotFile = screenshotFile;
    }

    @Override
    public int width() {
        return 160;
    }

    @Override
    public int height() {
        return 50;
    }

    @Override
    public Visibility getWantedVisibility() {
        return this.visibility;
    }

    @Override
    public void update(ToastManager manager, long fullyVisibleForMs) {
        // Stay visible for 5 seconds
        if (fullyVisibleForMs >= 5000L) {
            this.visibility = Visibility.HIDE;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, Font font, long fullyVisibleForMs) {
        if (this.firstRenderTime == -1L) {
            this.firstRenderTime = System.currentTimeMillis();
        }

        // 1. Draw polaroid white background frame
        int w = width();
        int h = height();
        graphics.fill(0, 0, w, h, 0xFFF0F0F0); // solid off-white background
        // Draw a thin grey border around the polaroid
        graphics.outline(0, 0, w, h, 0xFFD0D0D0);

        // 2. Draw screenshot picture inside photo frame
        int photoX = 5;
        int photoY = 5;
        int photoSize = 40;
        graphics.fill(photoX, photoY, photoX + photoSize, photoY + photoSize, 0xFF000000); // black placeholder

        if (this.texture == null) {
            this.texture = ScreenshotTextureCache.getOrLoad(this.screenshotFile);
        }

        if (this.texture != null) {
            // Draw screenshot
            graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                this.texture.id(),
                photoX,
                photoY,
                0.0f,
                0.0f,
                photoSize,
                photoSize,
                this.texture.width(),
                this.texture.height(),
                this.texture.width(),
                this.texture.height()
            );
        } else {
            // Draw a small warning cross / outline
            graphics.outline(photoX, photoY, photoSize, photoSize, 0xFFFF5555);
        }

        // Draw a tiny black inner border around the photo
        graphics.outline(photoX, photoY, photoSize, photoSize, 0xFF444444);

        // 3. Draw text next to the image
        int textX = photoX + photoSize + 8;
        graphics.text(font, Component.literal("Screenshot Saved"), textX, 12, 0xFF111111, false);
        
        // Draw the file name (trimmed if too long)
        String fileName = this.screenshotFile.getName();
        if (fileName.length() > 18) {
            fileName = fileName.substring(0, 15) + "...";
        }
        graphics.text(font, Component.literal(fileName), textX, 26, 0xFF666666, false);
    }
}
