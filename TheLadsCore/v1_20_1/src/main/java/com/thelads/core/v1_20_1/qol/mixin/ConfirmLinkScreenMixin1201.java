package com.thelads.core.v1_20_1.qol.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import com.thelads.core.client.LinkPreview;
import java.awt.image.BufferedImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ConfirmLinkScreen.class)
public abstract class ConfirmLinkScreenMixin1201 extends ConfirmScreen {
    private static ResourceLocation lads$activeLocation = null;
    private static DynamicTexture lads$activeTexture = null;

    @Shadow @Final private String url;

    @Unique private ResourceLocation lads$previewLocation;
    @Unique private DynamicTexture lads$dynamicTexture;
    @Unique private int lads$imgWidth;
    @Unique private int lads$imgHeight;

    public ConfirmLinkScreenMixin1201() {
        super(null, null, null);
    }

    public static void releaseActiveTexture() {
        if (lads$activeTexture != null) {
            try {
                lads$activeTexture.close();
            } catch (Throwable ignored) {}
            lads$activeTexture = null;
        }
        if (lads$activeLocation != null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getTextureManager() != null) {
                mc.getTextureManager().release(lads$activeLocation);
            }
            lads$activeLocation = null;
        }
    }

    @Inject(method = "addButtons(I)V", at = @At("TAIL"))
    private void lads$initPreview(int y, CallbackInfo ci) {
        lads$releaseTexture();
        if (!LinkPreview.isEnabled() || url == null || url.isEmpty()) return;

        int boxW = LinkPreview.THUMB_MAX_WIDTH + 8;
        int boxH = LinkPreview.THUMB_MAX_HEIGHT + 8;
        int boxX = width / 2 - boxW / 2;
        int boxY = 20;

        LinkPreview.Entry entry = LinkPreview.getEntry(url);
        if (entry != null && entry.getStatus() == LinkPreview.Status.LOADED && entry.getThumbnail() != null) {
            lads$uploadTexture(entry.getThumbnail());
        } else {
            Button loadBtn = Button.builder(Component.literal("Load preview"), b -> {
                b.visible = false;
                b.active = false;
                LinkPreview.request(url, e -> {
                    if (e != null && e.getThumbnail() != null) {
                        Minecraft.getInstance().execute(() -> lads$uploadTexture(e.getThumbnail()));
                    }
                });
            }).bounds(width / 2 - 50, boxY + boxH / 2 - 10, 100, 20).build();
            this.addRenderableWidget(loadBtn);
        }
    }

    @Unique
    private void lads$uploadTexture(BufferedImage img) {
        if (img == null) return;
        lads$releaseTexture();
        int w = img.getWidth(), h = img.getHeight();
        this.lads$imgWidth = w;
        this.lads$imgHeight = h;
        NativeImage ni = new NativeImage(w, h, true);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                ni.setPixelRGBA(x, y, img.getRGB(x, y));
            }
        }
        DynamicTexture dt = new DynamicTexture(ni);
        this.lads$dynamicTexture = dt;
        this.lads$previewLocation = Minecraft.getInstance().getTextureManager().register("lads_link_preview", dt);
        lads$activeTexture = dt;
        lads$activeLocation = this.lads$previewLocation;
    }

    @Unique
    private void lads$releaseTexture() {
        if (this.lads$dynamicTexture != null) {
            try {
                this.lads$dynamicTexture.close();
            } catch (Throwable ignored) {}
            this.lads$dynamicTexture = null;
        }
        if (this.lads$previewLocation != null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getTextureManager() != null) {
                mc.getTextureManager().release(this.lads$previewLocation);
            }
            if (lads$activeLocation == this.lads$previewLocation) {
                lads$activeLocation = null;
                lads$activeTexture = null;
            }
            this.lads$previewLocation = null;
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("TAIL"))
    private void lads$renderPreview(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!LinkPreview.isEnabled() || url == null) return;

        int boxW = LinkPreview.THUMB_MAX_WIDTH + 8;
        int boxH = LinkPreview.THUMB_MAX_HEIGHT + 8;
        int boxX = width / 2 - boxW / 2;
        int boxY = 20;

        graphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xDD101018);
        graphics.fill(boxX, boxY, boxX + boxW, boxY + 1, 0xFF404050);
        graphics.fill(boxX, boxY + boxH - 1, boxX + boxW, boxY + boxH, 0xFF404050);
        graphics.fill(boxX, boxY, boxX + 1, boxY + boxH, 0xFF404050);
        graphics.fill(boxX + boxW - 1, boxY, boxX + boxW, boxY + boxH, 0xFF404050);

        LinkPreview.Entry entry = LinkPreview.getEntry(url);
        if (entry != null && entry.getStatus() == LinkPreview.Status.LOADED && lads$previewLocation != null) {
            int imgX = width / 2 - lads$imgWidth / 2;
            int imgY = boxY + 4 + (LinkPreview.THUMB_MAX_HEIGHT - lads$imgHeight) / 2;
            graphics.blit(lads$previewLocation, imgX, imgY, 0.0F, 0.0F, lads$imgWidth, lads$imgHeight, lads$imgWidth, lads$imgHeight);
        } else if (entry != null && entry.getStatus() == LinkPreview.Status.PENDING) {
            graphics.drawCenteredString(font, "\u00A77Loading image preview...", width / 2, boxY + boxH / 2 - 4, 0xAAAAAA);
        } else if (entry != null && entry.getStatus() == LinkPreview.Status.NOT_ALLOWED) {
            graphics.drawCenteredString(font, "\u00A7cPreview blocked (insecure/private)", width / 2, boxY + boxH / 2 - 4, 0xFF5555);
        } else if (entry != null && entry.getStatus() == LinkPreview.Status.NOT_IMAGE) {
            graphics.drawCenteredString(font, "\u00A78No image preview available", width / 2, boxY + boxH / 2 - 4, 0x888888);
        } else if (entry != null && entry.getStatus() == LinkPreview.Status.FAILED) {
            graphics.drawCenteredString(font, "\u00A7cFailed to load preview", width / 2, boxY + boxH / 2 - 4, 0xFF8888);
        }
    }
}
