package com.thelads.core.v26_2.gui;

import com.thelads.core.client.auth.AccountSwitcherScreen;
import com.thelads.core.v26_2.adapter.GuiGraphicsExtractorLadsAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class AccountSwitcherScreen26 extends Screen {
    private final Screen parent;
    private final AccountSwitcherScreen ui;

    public AccountSwitcherScreen26(Screen parent) {
        super(Component.literal("Account Switcher"));
        this.parent = parent;
        this.ui = new AccountSwitcherScreen(new LiveSession26());
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
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {}

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        ui.render(new GuiGraphicsExtractorLadsAdapter(g, this.font), mouseX, mouseY);
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDouble) {
        if (ui.mouseClicked(event.x(), event.y(), event.button())) {
            return true;
        }
        return super.mouseClicked(event, isDouble);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (ui.mouseScrolled(mouseX, mouseY, verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void onClose() {
        ui.cancel();
        Minecraft.getInstance().setScreenAndShow(parent);
    }
}
