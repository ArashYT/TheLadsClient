package com.thelads.core.v1_21_11.gui;

import com.thelads.core.client.auth.AccountSwitcherScreen;
import com.thelads.core.v1_21_11.adapter.GuiGraphicsLadsAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class AccountSwitcherScreen12111 extends Screen {
    private final Screen parent;
    private final AccountSwitcherScreen ui;

    public AccountSwitcherScreen12111(Screen parent) {
        super(Component.literal("Account Switcher"));
        this.parent = parent;
        this.ui = new AccountSwitcherScreen(new LiveSession12111());
        this.ui.setOnClose(this::onClose);
    }

    public AccountSwitcherScreen ui() {
        return ui;
    }

    @Override
    public void tick() {
        Minecraft.getInstance().schedule(ui::tick); // queued: leaving a world runs game ticks of its own, so not from inside one
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {}

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        ui.render(new GuiGraphicsLadsAdapter(g, this.font), mouseX, mouseY);
        super.render(g, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDouble) {
        if (ui.mouseClicked(event.x(), event.y(), event.button())) {
            return true;
        }
        return super.mouseClicked(event, isDouble);
    }

    @Override
    public void onClose() {
        ui.cancel();
        Minecraft.getInstance().setScreen(parent);
    }
}
