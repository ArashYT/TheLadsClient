package com.thelads.core.v1_20_1.feature.screenshots;

import com.mojang.blaze3d.platform.NativeImage;
import com.thelads.core.client.gui.LadsPalette;
import java.io.File;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.Util;
import org.jetbrains.annotations.NotNull;

/** The "Screenshot Captured" toast with a small preview of the picture. */
public class NativeScreenshotToast implements Toast {
    private static final AtomicInteger IDS = new AtomicInteger();
    /** Preview textures of toasts that are gone (or sliding out): released once their slide-out is over. */
    private static final ArrayDeque<Expiring> EXPIRING = new ArrayDeque<>();
    private record Expiring(ResourceLocation texture, long releaseAt) {}
    private final File file;
    private final Component title;
    private final Component description;
    private volatile ResourceLocation textureId;
    private boolean released;

    public NativeScreenshotToast(File file) {
        this.file = file;
        this.title = Component.literal("Screenshot Captured");
        this.description = Component.literal(file.getName());
        loadThumbnailAsync();
    }

    private void loadThumbnailAsync() {
        ScreenshotFileIO.IMAGE_EXECUTOR.execute(() -> {
            try {
                NativeImage image = ScreenshotViewerUtils.readNative(file, true);
                Minecraft.getInstance().execute(() -> {
                    try {
                        DynamicTexture texture = new DynamicTexture(image);
                        ResourceLocation id = new ResourceLocation("theladscore", "screenshot_preview_" + IDS.incrementAndGet());
                        Minecraft.getInstance().getTextureManager().register(id, texture);
                        texture.setFilter(true, false);
                        if (released) { // the toast was already gone by the time the picture was decoded
                            Minecraft.getInstance().getTextureManager().release(id);
                        } else {
                            this.textureId = id;
                        }
                    } catch (Throwable ignored) {
                        image.close();
                    }
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
    public @NotNull Visibility render(GuiGraphics graphics, ToastComponent toastManager, long currentTime) {
        int w = width();
        int h = height();

        // Background card
        graphics.fill(0, 0, w, h, 0xF0130B10);
        graphics.fill(1, 1, w - 1, h - 1, 0xF01E1219);
        // Top accent line
        graphics.fill(0, 0, w, 2, LadsPalette.ACCENT);

        int textX = 8;
        ResourceLocation id = textureId;
        if (id != null) {
            int thumbW = 44;
            int thumbH = 26;
            int thumbX = 6;
            int thumbY = (h - thumbH) / 2 + 1;
            ScreenshotViewerUtils.drawTexture(graphics, id, thumbX, thumbY, thumbW, thumbH, 0, 0, thumbW, thumbH, thumbW, thumbH);
            graphics.fill(thumbX - 1, thumbY - 1, thumbX + thumbW + 1, thumbY, 0x55FFFFFF);
            graphics.fill(thumbX - 1, thumbY + thumbH, thumbX + thumbW + 1, thumbY + thumbH + 1, 0x55FFFFFF);
            graphics.fill(thumbX - 1, thumbY, thumbX, thumbY + thumbH, 0x55FFFFFF);
            graphics.fill(thumbX + thumbW, thumbY, thumbX + thumbW + 1, thumbY + thumbH, 0x55FFFFFF);
            textX = thumbX + thumbW + 8;
        }

        graphics.drawString(toastManager.getMinecraft().font, title, textX, 7, LadsPalette.TEXT, false);
        graphics.drawString(toastManager.getMinecraft().font, description, textX, 19, LadsPalette.MUTED, false);

        // currentTime is the time since the toast finished sliding in.
        if (currentTime >= 5000L * toastManager.getNotificationDisplayTimeMultiplier()) {
            if (!released) {
                released = true;
                ResourceLocation done = textureId;
                if (done != null) EXPIRING.add(new Expiring(done, Util.getMillis() + 1000L)); // the slide-out takes 600 ms
            }
            return Visibility.HIDE;
        }
        return Visibility.SHOW;
    }

    /** Releases the preview textures of hidden toasts; called every client tick. */
    public static void releaseExpired() {
        Expiring next;
        long now = Util.getMillis();
        while ((next = EXPIRING.peek()) != null && next.releaseAt() <= now) {
            EXPIRING.poll();
            Minecraft.getInstance().getTextureManager().release(next.texture());
        }
    }
}
