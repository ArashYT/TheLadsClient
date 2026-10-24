package com.thelads.core.v1_20_1.qol.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import com.thelads.core.client.LinkPreview;
import java.awt.image.BufferedImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.renderer.texture.DynamicTexture;
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
    @Shadow @Final private String url;

    @Unique private ResourceLocation lads$previewLocation;
    @Unique private int lads$imgWidth;
    @Unique private int lads$imgHeight;

    public ConfirmLinkScreenMixin1201() {
        super(null, null, null);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void lads$initPreview(CallbackInfo ci) {
        if (url != null && !url.isEmpty()) {
            LinkPreview.request(url, entry -> {
                if (entry != null && entry.getThumbnail() != null) {
                    Minecraft.getInstance().execute(() -> lads$uploadTexture(entry.getThumbnail()));
                }
            });
        }
    }

    @Unique
    private void lads$uploadTexture(BufferedImage img) {
        if (img == null) return;
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
        this.lads$previewLocation = Minecraft.getInstance().getTextureManager().register("lads_link_preview", dt);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void lads$renderPreview(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        LinkPreview.Entry entry = LinkPreview.getEntry(url);
        if (entry == null) return;

        int boxW = LinkPreview.THUMB_MAX_WIDTH + 8;
        int boxH = LinkPreview.THUMB_MAX_HEIGHT + 8;
        int boxX = width / 2 - boxW / 2;
        int boxY = 20;

        graphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xDD101018);
        graphics.fill(boxX, boxY, boxX + boxW, boxY + 1, 0xFF404050);
        graphics.fill(boxX, boxY + boxH - 1, boxX + boxW, boxY + boxH, 0xFF404050);
        graphics.fill(boxX, boxY, boxX + 1, boxY + boxH, 0xFF404050);
        graphics.fill(boxX + boxW - 1, boxY, boxX + boxW, boxY + boxH, 0xFF404050);

        if (entry.getStatus() == LinkPreview.Status.LOADED && lads$previewLocation != null) {
            int imgX = width / 2 - lads$imgWidth / 2;
            int imgY = boxY + 4 + (LinkPreview.THUMB_MAX_HEIGHT - lads$imgHeight) / 2;
            graphics.blit(lads$previewLocation, imgX, imgY, 0.0F, 0.0F, lads$imgWidth, lads$imgHeight, lads$imgWidth, lads$imgHeight);
        } else if (entry.getStatus() == LinkPreview.Status.PENDING) {
            graphics.drawCenteredString(font, "\u00A77Loading image preview...", width / 2, boxY + boxH / 2 - 4, 0xAAAAAA);
        } else {
            graphics.drawCenteredString(font, "\u00A78No image preview available", width / 2, boxY + boxH / 2 - 4, 0x888888);
        }
    }
}
