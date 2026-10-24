package com.thelads.core.v1_8_9.qol.mixin;

import com.thelads.core.client.LinkPreview;
import java.awt.image.BufferedImage;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiConfirmOpenLink;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiConfirmOpenLink.class)
public abstract class GuiConfirmOpenLinkMixin189 extends GuiScreen {
    @Shadow @Final private String linkText;

    @Unique private ResourceLocation lads$previewLocation;
    @Unique private BufferedImage lads$loadedImage;

    @Inject(method = "initGui", at = @At("TAIL"))
    private void lads$initPreview(CallbackInfo ci) {
        if (linkText != null && !linkText.isEmpty()) {
            LinkPreview.request(linkText, entry -> {
                if (entry != null && entry.getThumbnail() != null && mc != null) {
                    mc.addScheduledTask(() -> lads$uploadTexture(entry.getThumbnail()));
                }
            });
        }
    }

    @Unique
    private void lads$uploadTexture(BufferedImage img) {
        if (img == null || mc == null) return;
        this.lads$loadedImage = img;
        this.lads$previewLocation = mc.getTextureManager().getDynamicTextureLocation("lads_link_preview", new DynamicTexture(img));
    }

    @Inject(method = "drawScreen", at = @At("TAIL"))
    private void lads$renderPreview(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        LinkPreview.Entry entry = LinkPreview.getEntry(linkText);
        if (entry == null) return;

        int boxW = LinkPreview.THUMB_MAX_WIDTH + 8;
        int boxH = LinkPreview.THUMB_MAX_HEIGHT + 8;
        int boxX = width / 2 - boxW / 2;
        int boxY = 18;

        Gui.drawRect(boxX, boxY, boxX + boxW, boxY + boxH, 0xDD101018);
        Gui.drawRect(boxX, boxY, boxX + boxW, boxY + 1, 0xFF404050);
        Gui.drawRect(boxX, boxY + boxH - 1, boxX + boxW, boxY + boxH, 0xFF404050);
        Gui.drawRect(boxX, boxY, boxX + 1, boxY + boxH, 0xFF404050);
        Gui.drawRect(boxX + boxW - 1, boxY, boxX + boxW, boxY + boxH, 0xFF404050);

        if (entry.getStatus() == LinkPreview.Status.LOADED && lads$previewLocation != null && lads$loadedImage != null) {
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            mc.getTextureManager().bindTexture(lads$previewLocation);
            int imgW = lads$loadedImage.getWidth();
            int imgH = lads$loadedImage.getHeight();
            int imgX = width / 2 - imgW / 2;
            int imgY = boxY + 4 + (LinkPreview.THUMB_MAX_HEIGHT - imgH) / 2;
            Gui.drawModalRectWithCustomSizedTexture(imgX, imgY, 0, 0, imgW, imgH, imgW, imgH);
        } else if (entry.getStatus() == LinkPreview.Status.PENDING) {
            drawCenteredString(fontRendererObj, "\u00A77Loading image preview...", width / 2, boxY + boxH / 2 - 4, 0xAAAAAA);
        } else {
            drawCenteredString(fontRendererObj, "\u00A78No image preview available", width / 2, boxY + boxH / 2 - 4, 0x888888);
        }
    }
}
