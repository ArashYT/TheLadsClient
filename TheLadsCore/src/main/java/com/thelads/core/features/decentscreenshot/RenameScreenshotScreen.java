/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 *  net.minecraft.client.gui.components.Button
 *  net.minecraft.client.gui.components.EditBox
 *  net.minecraft.client.gui.components.events.GuiEventListener
 *  net.minecraft.client.gui.screens.Screen
 *  net.minecraft.network.chat.Component
 */
package com.thelads.core.features.decentscreenshot;

import java.io.File;
import com.thelads.core.features.decentscreenshot.ScreenshotTextureCache;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class RenameScreenshotScreen
extends Screen {
    private final Screen galleryScreen;
    private final Screen viewerScreen;
    private final File screenshotFile;
    private EditBox editBox;

    public RenameScreenshotScreen(Screen galleryScreen, Screen viewerScreen, File screenshotFile) {
        super((Component)Component.literal((String)"Rename Screenshot"));
        this.galleryScreen = galleryScreen;
        this.viewerScreen = viewerScreen;
        this.screenshotFile = screenshotFile;
    }

    protected void init() {
        String filename = this.screenshotFile.getName();
        int lastDot = filename.lastIndexOf(46);
        String nameWithoutExtension = lastDot > 0 ? filename.substring(0, lastDot) : filename;
        String extension = lastDot > 0 ? filename.substring(lastDot) : ".png";
        this.editBox = new EditBox(this.font, this.width / 2 - 100, this.height / 2 - 20, 200, 20, (Component)Component.literal((String)"New Name"));
        this.editBox.setMaxLength(128);
        this.editBox.setValue(nameWithoutExtension);
        this.addRenderableWidget(this.editBox);
        this.setInitialFocus(this.editBox);
        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"Rename"), btn -> {
            File parentDir;
            File newFile;
            String newNameText = this.editBox.getValue().trim();
            if (!newNameText.isEmpty() && !newNameText.equals(nameWithoutExtension) && this.screenshotFile.renameTo(newFile = new File(parentDir = this.screenshotFile.getParentFile(), newNameText + extension))) {
                ScreenshotTextureCache.release(this.screenshotFile);
                this.minecraft.setScreenAndShow(this.galleryScreen);
            }
        }).pos(this.width / 2 - 102, this.height / 2 + 10).size(100, 20).build());
        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"Cancel"), btn -> this.onClose()).pos(this.width / 2 + 2, this.height / 2 + 10).size(100, 20).build());
    }

    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, -871757302);
        graphics.centeredText(this.font, this.title, this.width / 2, this.height / 2 - 40, -3287308);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    public void onClose() {
        this.minecraft.setScreenAndShow(this.viewerScreen);
    }
}

