package com.thelads.core.v26_2.qol.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import com.thelads.core.client.LinkPreview;
import java.awt.image.BufferedImage;
import java.net.URI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ConfirmLinkScreen.class)
public abstract class ConfirmLinkScreenMixin26 extends ConfirmScreen {
    private static Identifier lads$activeLocation = null;
    private static DynamicTexture lads$activeTexture = null;

    @Shadow @Final private URI url;

    @Unique private Identifier lads$previewLocation;
    @Unique private DynamicTexture lads$dynamicTexture;
    @Unique private int lads$imgWidth;
    @Unique private int lads$imgHeight;

    public ConfirmLinkScreenMixin26() {
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

    @Inject(method = "addButtons(Lnet/minecraft/client/gui/layouts/LinearLayout;)V", at = @At("TAIL"))
    private void lads$initPreview(LinearLayout layout, CallbackInfo ci) {
        lads$releaseTexture();
        if (!LinkPreview.isEnabled() || url == null) return;
        final String rawUrl = url.toString();
        if (rawUrl.isEmpty()) return;

        int boxW = LinkPreview.THUMB_MAX_WIDTH + 8;
        int boxH = LinkPreview.THUMB_MAX_HEIGHT + 8;
        int boxX = width / 2 - boxW / 2;
        int boxY = 20;

        LinkPreview.Entry entry = LinkPreview.getEntry(rawUrl);
        if (entry != null && entry.getStatus() == LinkPreview.Status.LOADED && entry.getThumbnail() != null) {
            lads$uploadTexture(entry.getThumbnail());
        } else {
            Button loadBtn = Button.builder(Component.literal("Load preview"), b -> {
                b.visible = false;
                b.active = false;
                LinkPreview.request(rawUrl, e -> {
                    if (e != null && e.getThumbnail() != null) {
                        Minecraft.getInstance().execute(() -> lads$uploadTexture(e.getThumbnail()));
                    }
                });
            }).bounds(width / 2 - 50, boxY + boxH / 2 - 10, 100, 20).build();
            this.addRenderableWidget(loadBtn);
        }

        // Render preview frame and content via Renderable widget, which Screen.extractRenderState automatically renders
        this.addRenderableOnly(new Renderable() {
            @Override
            public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
                graphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xDD101018);
                graphics.fill(boxX, boxY, boxX + boxW, boxY + 1, 0xFF404050);
                graphics.fill(boxX, boxY + boxH - 1, boxX + boxW, boxY + boxH, 0xFF404050);
                graphics.fill(boxX, boxY, boxX + 1, boxY + boxH, 0xFF404050);
                graphics.fill(boxX + boxW - 1, boxY, boxX + boxW, boxY + boxH, 0xFF404050);

                LinkPreview.Entry currentEntry = LinkPreview.getEntry(rawUrl);
                if (currentEntry != null && currentEntry.getStatus() == LinkPreview.Status.LOADED && lads$previewLocation != null) {
                    int imgX = width / 2 - lads$imgWidth / 2;
                    int imgY = boxY + 4 + (LinkPreview.THUMB_MAX_HEIGHT - lads$imgHeight) / 2;
                    graphics.blit(RenderPipelines.GUI_TEXTURED, lads$previewLocation, imgX, imgY, 0.0F, 0.0F, lads$imgWidth, lads$imgHeight, lads$imgWidth, lads$imgHeight);
                } else if (currentEntry != null && currentEntry.getStatus() == LinkPreview.Status.PENDING) {
                    String msg = "Loading image preview...";
                    graphics.text(font, msg, (width - font.width(msg)) / 2, boxY + boxH / 2 - 4, 0xAAAAAA, false);
                } else if (currentEntry != null && currentEntry.getStatus() == LinkPreview.Status.NOT_ALLOWED) {
                    String msg = "Preview blocked (insecure/private)";
                    graphics.text(font, msg, (width - font.width(msg)) / 2, boxY + boxH / 2 - 4, 0xFF5555, false);
                } else if (currentEntry != null && currentEntry.getStatus() == LinkPreview.Status.NOT_IMAGE) {
                    String msg = "Safe Web Link - No Image Preview";
                    graphics.text(font, msg, (width - font.width(msg)) / 2, boxY + boxH / 2 - 4, 0x888888, false);
                } else if (currentEntry != null && currentEntry.getStatus() == LinkPreview.Status.FAILED) {
                    String msg = "Failed to load preview";
                    graphics.text(font, msg, (width - font.width(msg)) / 2, boxY + boxH / 2 - 4, 0xFF8888, false);
                }
            }
        });
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
                ni.setPixel(x, y, img.getRGB(x, y));
            }
        }
        DynamicTexture dt = new DynamicTexture(() -> "lads_link_preview", ni);
        this.lads$dynamicTexture = dt;
        Identifier id = Identifier.fromNamespaceAndPath("theladscore", "link_preview_" + (System.currentTimeMillis() % 1000));
        Minecraft.getInstance().getTextureManager().register(id, dt);
        this.lads$previewLocation = id;
        lads$activeTexture = dt;
        lads$activeLocation = id;
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
}
