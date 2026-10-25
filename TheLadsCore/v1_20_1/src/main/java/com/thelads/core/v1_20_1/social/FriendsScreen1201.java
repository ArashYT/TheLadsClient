package com.thelads.core.v1_20_1.social;

import com.thelads.core.client.social.ui.FriendsScreenLogic;
import com.thelads.core.v1_20_1.adapter.GuiGraphicsLadsAdapter1201;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Host screen for the common FriendsScreenLogic on 1.20.1: forwards render and input only. */
public final class FriendsScreen1201 extends Screen {
    private final Screen parent;
    private final FriendsScreenLogic ui;

    public FriendsScreen1201(Screen parent) {
        super(Component.literal("Friends"));
        this.parent = parent;
        this.ui = FriendsScreenLogic.social(() -> Minecraft.getInstance().setScreen(parent));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        ui.render(new GuiGraphicsLadsAdapter1201(g, font), mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        return ui.mouseClicked(mx, my, button) || super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        return ui.mouseScrolled(mx, my, delta) || super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return ui.keyPressed(keyCode, scanCode, modifiers) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return ui.charTyped(codePoint) || super.charTyped(codePoint, modifiers);
    }

    @Override
    public void onClose() {
        ui.close();
    }

    @Override
    public boolean isPauseScreen() {
        return parent == null || parent.isPauseScreen();
    }
}
