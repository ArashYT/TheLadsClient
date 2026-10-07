package com.thelads.core.v26_2.feature.screenshots;

import com.mojang.blaze3d.platform.NativeImage;
import com.thelads.core.client.gui.LadsPalette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

public class NativeScreenshotToast implements Toast {
    private final File file;
    private final Component title;
    private final Component description;
    private Identifier textureId;
    private DynamicTexture dynamicTexture;
    private long firstRender = 0;
    private Visibility visibility = Visibility.SHOW;

    public NativeScreenshotToast(File file) {
        this.file = file;
        this.title = Component.literal("Screenshot Captured");
        this.description = Component.literal(file.getName());
        loadThumbnailAsync();
    }

    private void loadThumbnailAsync() {
        ScreenshotFileIO.IMAGE_EXECUTOR.execute(() -> {
            try (InputStream in = new FileInputStream(file)) {
                NativeImage image = NativeImage.read(in);
                Minecraft.getInstance().execute(() -> {
                    try {
                        this.dynamicTexture = new DynamicTexture(() -> "Lads screenshot preview", image);
                        String path = "screenshot_preview_" + Math.abs(file.getName().hashCode());
                        this.textureId = Identifier.fromNamespaceAndPath("theladscore", path);
                        Minecraft.getInstance().getTextureManager().register(this.textureId, this.dynamicTexture);
                    } catch (Throwable ignored) {}
                });
            } catch (Throwable ignored) {}
        });
    }

    @Override
    public int width() {
        return 190;
    }

    @Override
    public int height() {
        return 38;
    }

    @Override
    public @NotNull Visibility getWantedVisibility() {
        return visibility;
    }

    @Override
    public void update(ToastManager toastManager, long currentTime) {
        if (firstRender == 0) return;
        if (currentTime - firstRender >= 5000L * toastManager.getNotificationDisplayTimeMultiplier()) {
            visibility = Visibility.HIDE;
            if (dynamicTexture != null) {
                dynamicTexture.close();
                dynamicTexture = null;
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, Font font, long currentTime) {
        if (firstRender == 0) {
            firstRender = currentTime;
        }

        int w = width();
        int h = height();

        // Background card
        graphics.fill(0, 0, w, h, 0xF0130B10);
        graphics.fill(1, 1, w - 1, h - 1, 0xF01E1219);
        // Top accent line
        graphics.fill(0, 0, w, 2, LadsPalette.ACCENT);

        int textX = 8;
        if (textureId != null) {
            int thumbW = 44;
            int thumbH = 26;
            int thumbX = 6;
            int thumbY = (h - thumbH) / 2 + 1;
            ScreenshotViewerUtils.drawTexture(graphics, textureId, thumbX, thumbY, thumbW, thumbH, 0, 0, thumbW, thumbH, thumbW, thumbH);
            graphics.fill(thumbX - 1, thumbY - 1, thumbX + thumbW + 1, thumbY, 0x55FFFFFF);
            graphics.fill(thumbX - 1, thumbY + thumbH, thumbX + thumbW + 1, thumbY + thumbH + 1, 0x55FFFFFF);
            graphics.fill(thumbX - 1, thumbY, thumbX, thumbY + thumbH, 0x55FFFFFF);
            graphics.fill(thumbX + thumbW, thumbY, thumbX + thumbW + 1, thumbY + thumbH, 0x55FFFFFF);
            textX = thumbX + thumbW + 8;
        }

        graphics.text(font, title, textX, 7, LadsPalette.TEXT, false);
        graphics.text(font, description, textX, 19, LadsPalette.MUTED, false);
    }
}
