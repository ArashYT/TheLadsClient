/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 *  net.minecraft.client.gui.components.Button
 *  net.minecraft.client.gui.components.events.GuiEventListener
 *  net.minecraft.client.gui.screens.Screen
 *  net.minecraft.client.input.KeyEvent
 *  net.minecraft.client.input.MouseButtonEvent
 *  net.minecraft.client.renderer.RenderPipelines
 *  net.minecraft.network.chat.Component
 *  net.minecraft.util.Util
 */
package com.thelads.core.features.decentscreenshot;

import java.io.File;
import java.util.List;
import com.thelads.core.features.decentscreenshot.ScreenshotViewerScreen;
import com.thelads.core.features.decentscreenshot.ScreenshotTextureCache;
import com.thelads.core.features.decentscreenshot.ScreenshotFinder;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

public class ScreenshotGalleryScreen
extends Screen {
    private static final int COLS = 4;
    private static final int CELL_PAD = 6;
    private static final int TOP_BAR_H = 28;
    private static final int SCROLL_STEP = 32;
    private static final int CELL_RADIUS = 4;
    private final Screen parent;
    private List<File> screenshots;
    private int scrollOffset = 0;
    private int cellSize;
    private int totalGridHeight;

    public ScreenshotGalleryScreen(Screen parent) {
        super((Component)Component.literal((String)"Screenshots"));
        this.parent = parent;
    }

    protected void init() {
        this.screenshots = ScreenshotFinder.getScreenshots();
        this.scrollOffset = 0;
        int usableW = this.width - 30;
        this.cellSize = Math.max(40, usableW / 4);
        int rows = (int)Math.ceil((double)this.screenshots.size() / 4.0);
        this.totalGridHeight = rows * (this.cellSize + 6) + 6;
        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u2715 Close"), btn -> this.onClose()).pos(this.width - 70, 5).size(64, 18).build());
        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u27f3 Refresh"), btn -> {
            ScreenshotTextureCache.releaseAll();
            this.rebuildWidgets();
        }).pos(6, 5).size(70, 18).build());
        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\ud83d\udcc1 Open Folder"), btn -> Util.getPlatform().openFile(ScreenshotFinder.getScreenshotDirectory())).pos(82, 5).size(84, 18).build());
    }

    public void onClose() {
        ScreenshotTextureCache.releaseAll();
        this.minecraft.setScreenAndShow(this.parent);
    }

    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, -15658735);
        graphics.fill(0, 0, this.width, 28, -14803410);
        graphics.centeredText(this.font, (Component)Component.literal((String)("\ud83d\udcf7 Screenshots  (" + this.screenshots.size() + ")")), this.width / 2, 9, -3287308);
        graphics.fill(0, 28, this.width, 29, -12236966);
        int bodyTop = 29;
        int hoveredIndex = -1;
        for (int i = 0; i < this.screenshots.size(); ++i) {
            boolean hovered;
            int col = i % 4;
            int row = i / 4;
            int x = 6 + col * (this.cellSize + 6);
            int y = bodyTop + 6 + row * (this.cellSize + 6) - this.scrollOffset;
            if (y + this.cellSize < bodyTop || y > this.height) continue;
            File file = this.screenshots.get(i);
            boolean bl = hovered = mouseX >= x && mouseX < x + this.cellSize && mouseY >= y && mouseY < y + this.cellSize;
            if (hovered) {
                hoveredIndex = i;
            }
            this.fillRoundedRect(graphics, x, y, this.cellSize, this.cellSize, 4, hovered ? -10986640 : -13553084);
            ScreenshotTextureCache.ScreenshotTexture tex = ScreenshotTextureCache.getOrLoad(file);
            if (tex != null) {
                double scale = Math.max((double)this.cellSize / (double)tex.width(), (double)this.cellSize / (double)tex.height());
                int sourceW = Math.max(1, Math.min(tex.width(), (int)Math.round((double)this.cellSize / scale)));
                int sourceH = Math.max(1, Math.min(tex.height(), (int)Math.round((double)this.cellSize / scale)));
                int sourceX = (tex.width() - sourceW) / 2;
                int sourceY = (tex.height() - sourceH) / 2;
                graphics.blit(RenderPipelines.GUI_TEXTURED, tex.id(), x, y, (float)sourceX, (float)sourceY, this.cellSize, this.cellSize, sourceW, sourceH, tex.width(), tex.height());
                this.maskRoundedCorners(graphics, x, y, this.cellSize, this.cellSize, -15658735);
            } else {
                graphics.centeredText(this.font, (Component)Component.literal((String)"?"), x + this.cellSize / 2, y + this.cellSize / 2 - 4, -10986640);
            }
            if (!hovered) continue;
            this.drawRoundedBorder(graphics, x, y, this.cellSize, this.cellSize, 4, -3430665);
        }
        this.renderScrollbar(graphics, bodyTop);
        if (this.screenshots.isEmpty()) {
            graphics.centeredText(this.font, (Component)Component.literal((String)"No screenshots found."), this.width / 2, this.height / 2 - 8, -9670522);
            graphics.centeredText(this.font, (Component)Component.literal((String)"Take a screenshot with F2, then press F10."), this.width / 2, this.height / 2 + 4, -12236966);
        }
        if (hoveredIndex >= 0) {
            File hFile = this.screenshots.get(hoveredIndex);
            graphics.setTooltipForNextFrame(this.font, (Component)Component.literal((String)hFile.getName()), mouseX, mouseY);
        }
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private void renderScrollbar(GuiGraphicsExtractor graphics, int bodyTop) {
        int bodyH = this.height - bodyTop;
        if (this.totalGridHeight <= bodyH) {
            return;
        }
        int trackX = this.width - 6;
        int trackY = bodyTop;
        int trackH = bodyH;
        graphics.fill(trackX, trackY, trackX + 4, trackY + trackH, -13553084);
        float ratio = (float)bodyH / (float)this.totalGridHeight;
        int thumbH = Math.max(20, (int)((float)trackH * ratio));
        float thumbY = (float)trackY + (float)this.scrollOffset / (float)this.totalGridHeight * (float)trackH;
        graphics.fill(trackX, (int)thumbY, trackX + 4, (int)thumbY + thumbH, -10986640);
    }

    private void fillRoundedRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius, int color) {
        g.fill(x + radius, y, x + w - radius, y + h, color);
        g.fill(x, y + radius, x + w, y + h - radius, color);
        g.fill(x + 2, y + 1, x + w - 2, y + h - 1, color);
        g.fill(x + 1, y + 2, x + w - 1, y + h - 2, color);
    }

    private void maskRoundedCorners(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + 2, y + 1, color);
        g.fill(x, y + 1, x + 1, y + 2, color);
        g.fill(x + w - 2, y, x + w, y + 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + 2, color);
        g.fill(x, y + h - 1, x + 2, y + h, color);
        g.fill(x, y + h - 2, x + 1, y + h - 1, color);
        g.fill(x + w - 2, y + h - 1, x + w, y + h, color);
        g.fill(x + w - 1, y + h - 2, x + w, y + h - 1, color);
    }

    private void drawRoundedBorder(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius, int color) {
        g.fill(x + radius, y, x + w - radius, y + 1, color);
        g.fill(x + radius, y + h - 1, x + w - radius, y + h, color);
        g.fill(x, y + radius, x + 1, y + h - radius, color);
        g.fill(x + w - 1, y + radius, x + w, y + h - radius, color);
        g.fill(x + 1, y + 2, x + 2, y + radius, color);
        g.fill(x + 2, y + 1, x + radius, y + 2, color);
        g.fill(x + w - 2, y + 2, x + w - 1, y + radius, color);
        g.fill(x + w - radius, y + 1, x + w - 2, y + 2, color);
        g.fill(x + 1, y + h - radius, x + 2, y + h - 2, color);
        g.fill(x + 2, y + h - 2, x + radius, y + h - 1, color);
        g.fill(x + w - 2, y + h - radius, x + w - 1, y + h - 2, color);
        g.fill(x + w - radius, y + h - 2, x + w - 2, y + h - 1, color);
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            double mouseX = event.x();
            double mouseY = event.y();
            int bodyTop = 29;
            for (int i = 0; i < this.screenshots.size(); ++i) {
                int col = i % 4;
                int row = i / 4;
                int x = 6 + col * (this.cellSize + 6);
                int y = bodyTop + 6 + row * (this.cellSize + 6) - this.scrollOffset;
                if (!(mouseX >= (double)x) || !(mouseX < (double)(x + this.cellSize)) || !(mouseY >= (double)y) || !(mouseY < (double)(y + this.cellSize))) continue;
                this.openViewer(i);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int bodyH = this.height - 29;
        int maxScroll = Math.max(0, this.totalGridHeight - bodyH);
        this.scrollOffset = (int)Math.max(0.0, Math.min((double)maxScroll, (double)this.scrollOffset - verticalAmount * 32.0));
        return true;
    }

    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    public boolean shouldCloseOnEsc() {
        return false;
    }

    private void openViewer(int index) {
        File file = this.screenshots.get(index);
        boolean hasPrev = index > 0;
        boolean hasNext = index < this.screenshots.size() - 1;
        this.minecraft.setScreenAndShow((Screen)new ScreenshotViewerScreen(this, file, () -> this.openViewer(index - 1), hasPrev, () -> this.openViewer(index + 1), hasNext));
    }
}

