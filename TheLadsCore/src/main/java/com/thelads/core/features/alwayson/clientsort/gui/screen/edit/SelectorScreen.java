/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.ChatFormatting
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.components.Button
 *  net.minecraft.client.gui.components.CycleButton
 *  net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
 *  net.minecraft.client.input.MouseButtonEvent
 *  net.minecraft.network.chat.CommonComponents
 *  net.minecraft.network.chat.Component
 *  org.jetbrains.annotations.NotNull
 */
package com.thelads.core.features.alwayson.clientsort.gui.screen.edit;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import com.thelads.core.features.alwayson.clientsort.config.Config;
import com.thelads.core.features.alwayson.clientsort.gui.TriggerButtonManager;
import com.thelads.core.features.alwayson.clientsort.gui.screen.edit.EditorScreen;
import com.thelads.core.features.alwayson.clientsort.gui.widget.TriggerButton;
import com.thelads.core.mixin.alwayson.clientsort.client.accessor.GuiGraphicsExtractorAccessor;
import com.thelads.core.mixin.alwayson.clientsort.client.accessor.GuiRenderStateAccessor;
import com.thelads.core.features.alwayson.clientsort.util.Localization;
import java.util.LinkedList;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public class SelectorScreen
extends Screen {
    private final Screen lastScreen;
    private final AbstractContainerScreen<?> underlay;
    private final LinkedList<TriggerButton> buttons = new LinkedList();

    public SelectorScreen(AbstractContainerScreen<?> underlay) {
        this(underlay, (Screen)underlay);
    }

    public SelectorScreen(AbstractContainerScreen<?> underlay, Screen lastScreen) {
        super((Component)Localization.localized("title", "groupSelector", new Object[0]));
        this.underlay = underlay;
        this.lastScreen = lastScreen;
    }

    @Override
    public void init() {
        super.init();
        this.underlay.init(this.width, this.height);
        this.reloadButtons();
        this.rebuildGui();
    }

    private void reloadButtons() {
        this.buttons.clear();
        this.buttons.addAll(TriggerButtonManager.getContainerButtons());
        this.buttons.addAll(TriggerButtonManager.getPlayerButtons());
    }

    /*
     * Issues handling annotations - annotations may be inaccurate
     */
    private void rebuildGui() {
        this.clearWidgets();
        @NotNull CycleButton toggleButton = CycleButton.booleanBuilder((Component)Localization.localized("editor", "enabled", new Object[0]).withStyle(ChatFormatting.GREEN), (Component)Localization.localized("editor", "disabled", new Object[0]).withStyle(ChatFormatting.RED), (boolean)Config.options().showButtons).create(this.width / 2 - 125, this.height - 22, 120, 20, (Component)Localization.localized("editor", "buttons", new Object[0]), (buttons, status) -> {
            Config.options().showButtons = status;
            Config.save();
            this.init();
        });
        this.addRenderableWidget(toggleButton);
        Button cancelButton = Button.builder((Component)CommonComponents.GUI_BACK, button -> this.onClose()).pos(this.width / 2 + 5, this.height - 22).size(120, 20).build();
        this.addRenderableWidget(cancelButton);
    }

    @Override
    public void extractRenderState(@NotNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.underlay.extractBackground(graphics, mouseX, mouseY, partialTick);
        this.underlay.extractRenderState(graphics, mouseX, mouseY, partialTick);
        ((GuiRenderStateAccessor)((GuiGraphicsExtractorAccessor)((Object)graphics)).clientsort$getGuiRenderState()).clientsort$setFirstStratumAfterBlur(Integer.MAX_VALUE);
        graphics.nextStratum();
        this.extractBlurredBackground(graphics);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font, this.title, this.width / 2, 2, -1);
        if (Config.options().showButtons) {
            for (TriggerButton cb : this.buttons) {
                cb.extractContents(graphics, mouseX, mouseY, partialTick);
            }
        }
    }

    @Override
    public void extractBackground(@NotNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (Minecraft.getInstance().level == null) {
            this.extractPanorama(graphics, partialTick);
        }
        this.extractMenuBackground(graphics);
    }

    @Override
    protected void extractBlurredBackground(@NotNull GuiGraphicsExtractor graphics) {
        int original = (Integer)Minecraft.getInstance().options.menuBackgroundBlurriness().get();
        Minecraft.getInstance().options.menuBackgroundBlurriness().set(6);
        super.extractBlurredBackground(graphics);
        Minecraft.getInstance().options.menuBackgroundBlurriness().set(original);
    }

    /*
     * Enabled aggressive block sorting
     */
    @Override
    public void onClose() {
        super.onClose();
        Screen screen = this.lastScreen;
        if (screen instanceof EditorScreen) {
            EditorScreen pes = (EditorScreen)screen;
            if (!Config.options().showButtons) {
                pes.onClose();
                return;
            }
        }
        this.lastScreen.init(this.width, this.height);
        Minecraft.getInstance().setScreen(this.lastScreen);
    }

    public boolean mouseClicked(@NotNull MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        for (TriggerButton cb : this.buttons) {
            if (!cb.isMouseOver(event.x(), event.y())) continue;
            cb.playDownSound(Minecraft.getInstance().getSoundManager());
            this.onClose();
            cb.openEditScreen();
        }
        return false;
    }
}
