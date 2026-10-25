package com.thelads.core.v1_8_9.qol.mixin;

import com.thelads.core.client.LinkPreview;
import java.awt.image.BufferedImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
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
    private static final int LADS_LOAD_PREVIEW_BTN_ID = 991204;

    @Shadow @Final private String linkText;

    @Unique private ResourceLocation lads$previewLocation;
    @Unique private BufferedImage lads$loadedImage;
    @Unique private GuiButton lads$loadBtn;

    @Inject(method = "initGui", at = @At("TAIL"))
    private void lads$initPreview(CallbackInfo ci) {
        lads$releaseTexture();
        if (!LinkPreview.isEnabled() || linkText == null || linkText.isEmpty()) return;

        int boxW = LinkPreview.THUMB_MAX_WIDTH + 8;
        int boxH = LinkPreview.THUMB_MAX_HEIGHT + 8;
        int boxX = width / 2 - boxW / 2;
        int boxY = 18;

        LinkPreview.Entry entry = LinkPreview.getEntry(linkText);
        if (entry != null && entry.getStatus() == LinkPreview.Status.LOADED && entry.getThumbnail() != null) {
            lads$uploadTexture(entry.getThumbnail());
        } else {
            lads$loadBtn = new GuiButton(LADS_LOAD_PREVIEW_BTN_ID, width / 2 - 50, boxY + boxH / 2 - 10, 100, 20, "Load preview");
            this.buttonList.add(lads$loadBtn);
        }
    }

    @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true)
    private void lads$onAction(GuiButton button, CallbackInfo ci) {
        if (button.id == LADS_LOAD_PREVIEW_BTN_ID) {
            ci.cancel();
            button.visible = false;
            button.enabled = false;
            LinkPreview.request(linkText, entry -> {
                if (entry != null && entry.getThumbnail() != null && mc != null) {
                    mc.addScheduledTask(() -> lads$uploadTexture(entry.getThumbnail()));
                }
            });
            return;
        }
        // Player clicked an action button (Yes, No, Copy) -> screen will close
        lads$releaseTexture();
    }

    @Unique
    private void lads$uploadTexture(BufferedImage img) {
        if (img == null || mc == null) return;
        lads$releaseTexture();
        this.lads$loadedImage = img;
        this.lads$previewLocation = mc.getTextureManager().getDynamicTextureLocation("lads_link_preview", new DynamicTexture(img));
        com.thelads.core.v1_8_9.qol.link.LinkPreviewTextureHelper189.setActive(this.lads$previewLocation);
    }

    @Unique
    private void lads$releaseTexture() {
        if (this.lads$previewLocation != null) {
            if (mc != null && mc.getTextureManager() != null) {
                mc.getTextureManager().deleteTexture(this.lads$previewLocation);
            }

            this.lads$previewLocation = null;
            this.lads$loadedImage = null;
        }
    }

    @Inject(method = "drawScreen", at = @At("TAIL"))
    private void lads$renderPreview(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (!LinkPreview.isEnabled() || linkText == null) return;

        int boxW = LinkPreview.THUMB_MAX_WIDTH + 8;
        int boxH = LinkPreview.THUMB_MAX_HEIGHT + 8;
        int boxX = width / 2 - boxW / 2;
        int boxY = 18;

        Gui.drawRect(boxX, boxY, boxX + boxW, boxY + boxH, 0xDD101018);
        Gui.drawRect(boxX, boxY, boxX + boxW, boxY + 1, 0xFF404050);
        Gui.drawRect(boxX, boxY + boxH - 1, boxX + boxW, boxY + boxH, 0xFF404050);
        Gui.drawRect(boxX, boxY, boxX + 1, boxY + boxH, 0xFF404050);
        Gui.drawRect(boxX + boxW - 1, boxY, boxX + boxW, boxY + boxH, 0xFF404050);

        LinkPreview.Entry entry = LinkPreview.getEntry(linkText);
        if (entry != null && entry.getStatus() == LinkPreview.Status.LOADED && lads$previewLocation != null && lads$loadedImage != null) {
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            mc.getTextureManager().bindTexture(lads$previewLocation);
            int imgW = lads$loadedImage.getWidth();
            int imgH = lads$loadedImage.getHeight();
            int imgX = width / 2 - imgW / 2;
            int imgY = boxY + 4 + (LinkPreview.THUMB_MAX_HEIGHT - imgH) / 2;
            Gui.drawModalRectWithCustomSizedTexture(imgX, imgY, 0, 0, imgW, imgH, imgW, imgH);
        } else if (entry != null && entry.getStatus() == LinkPreview.Status.PENDING) {
            drawCenteredString(fontRendererObj, "\u00A77Loading image preview...", width / 2, boxY + boxH / 2 - 4, 0xAAAAAA);
        } else if (entry != null && entry.getStatus() == LinkPreview.Status.NOT_ALLOWED) {
            drawCenteredString(fontRendererObj, "\u00A7cPreview blocked (insecure/private)", width / 2, boxY + boxH / 2 - 4, 0xFF5555);
        } else if (entry != null && entry.getStatus() == LinkPreview.Status.NOT_IMAGE) {
            drawCenteredString(fontRendererObj, "\u00A78No image preview available", width / 2, boxY + boxH / 2 - 4, 0x888888);
        } else if (entry != null && entry.getStatus() == LinkPreview.Status.FAILED) {
            drawCenteredString(fontRendererObj, "\u00A7cFailed to load preview", width / 2, boxY + boxH / 2 - 4, 0xFF8888);
        }
    }
}
