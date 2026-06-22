/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 *  net.minecraft.client.gui.components.Button
 *  net.minecraft.client.gui.components.events.GuiEventListener
 *  net.minecraft.client.gui.screens.Screen
 *  net.minecraft.client.input.KeyEvent
 *  net.minecraft.client.renderer.RenderPipelines
 *  net.minecraft.network.chat.Component
 */
package com.thelads.core.features.decentscreenshot;

import java.io.File;
import com.thelads.core.features.decentscreenshot.RenameScreenshotScreen;
import com.thelads.core.features.decentscreenshot.ScreenshotTextureCache;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

public class ScreenshotViewerScreen
extends Screen {
    private final Screen parent;
    private final File screenshotFile;
    private ScreenshotTextureCache.ScreenshotTexture texture;
    private final Runnable onPrev;
    private final Runnable onNext;
    private final boolean hasPrev;
    private final boolean hasNext;

    public ScreenshotViewerScreen(Screen parent, File screenshotFile, Runnable onPrev, boolean hasPrev, Runnable onNext, boolean hasNext) {
        super((Component)Component.literal((String)screenshotFile.getName()));
        this.parent = parent;
        this.screenshotFile = screenshotFile;
        this.onPrev = onPrev;
        this.onNext = onNext;
        this.hasPrev = hasPrev;
        this.hasNext = hasNext;
    }

    protected void init() {
        this.texture = ScreenshotTextureCache.getOrLoad(this.screenshotFile);
        int btnY = this.height - 24;
        int btnW = 60;
        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u25c0 Back"), btn -> this.minecraft.setScreenAndShow(this.parent)).pos(6, btnY).size(btnW, 18).build());
        Button prevBtn = Button.builder((Component)Component.literal((String)"\u2190 Prev"), btn -> this.onPrev.run()).pos(this.width / 2 - 66, btnY).size(60, 18).build();
        prevBtn.active = this.hasPrev;
        this.addRenderableWidget(prevBtn);
        Button nextBtn = Button.builder((Component)Component.literal((String)"Next \u2192"), btn -> this.onNext.run()).pos(this.width / 2 + 6, btnY).size(60, 18).build();
        nextBtn.active = this.hasNext;
        this.addRenderableWidget(nextBtn);
        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"Rename"), btn -> this.minecraft.setScreenAndShow((Screen)new RenameScreenshotScreen(this.parent, this, this.screenshotFile))).pos(this.width - 132, btnY).size(60, 18).build());
        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"Delete"), btn -> {
            if (btn.getMessage().getString().equals("Delete")) {
                btn.setMessage((Component)Component.literal((String)"Confirm?"));
            } else {
                if (this.screenshotFile.exists()) {
                    this.screenshotFile.delete();
                }
                ScreenshotTextureCache.release(this.screenshotFile);
                this.minecraft.setScreenAndShow(this.parent);
            }
        }).pos(this.width - 66, btnY).size(60, 18).build());
    }

    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, -16119286);
        if (this.texture != null) {
            int maxFrameW = Math.max(160, (int)((double)this.width * 0.86));
            int maxFrameH = Math.max(120, (int)((double)(this.height - 48) * 0.78));
            double scale = Math.min((double)maxFrameW / (double)this.texture.width(), (double)maxFrameH / (double)this.texture.height());
            int imageW = Math.max(1, (int)Math.round((double)this.texture.width() * scale));
            int imageH = Math.max(1, (int)Math.round((double)this.texture.height() * scale));
            int imgX = (this.width - imageW) / 2;
            int imgY = 18 + (maxFrameH - imageH) / 2;
            graphics.fill(imgX - 2, imgY - 2, imgX + imageW + 2, imgY + imageH + 2, -13553084);
            graphics.blit(RenderPipelines.GUI_TEXTURED, this.texture.id(), imgX, imgY, 0.0f, 0.0f, imageW, imageH, this.texture.width(), this.texture.height(), this.texture.width(), this.texture.height());
        } else {
            graphics.centeredText(this.font, (Component)Component.literal((String)"\u26a0 Could not load image"), this.width / 2, this.height / 2 - 8, -43691);
        }
        String label = this.screenshotFile.getName();
        graphics.centeredText(this.font, (Component)Component.literal((String)label), this.width / 2, 6, -3287308);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256) {
            this.minecraft.setScreenAndShow(this.parent);
            return true;
        }
        if (event.key() == 263 && this.hasPrev) {
            this.onPrev.run();
            return true;
        }
        if (event.key() == 262 && this.hasNext) {
            this.onNext.run();
            return true;
        }
        return super.keyPressed(event);
    }

    public boolean shouldCloseOnEsc() {
        return false;
    }
}

