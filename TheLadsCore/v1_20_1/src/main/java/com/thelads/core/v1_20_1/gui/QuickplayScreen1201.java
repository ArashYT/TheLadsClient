package com.thelads.core.v1_20_1.gui;

import com.thelads.core.client.hypixel.quickplay.QuickplayScreen;
import com.thelads.core.v1_20_1.adapter.GuiGraphicsLadsAdapter1201;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class QuickplayScreen1201 extends Screen {
    private final Screen parent;
    private final QuickplayScreen ui;

    public QuickplayScreen1201(Screen parent) {
        super(Component.literal("Hypixel Quickplay"));
        this.parent = parent;
        this.ui = new QuickplayScreen();
        this.ui.setOnClose(this::onClose);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        ui.render(new GuiGraphicsLadsAdapter1201(guiGraphics, font), mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        ui.handleMouseClick((int) mouseX, (int) mouseY, button, width, height);
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        ui.handleMouseScroll(amount);
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ui.keyPressed(keyCode)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (ui.charTyped(codePoint)) return true;
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
