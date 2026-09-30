package com.thelads.core.v1_21_1.gui;

import com.thelads.core.client.auth.AccountSwitcherScreen;
import com.thelads.core.v1_21_1.adapter.GuiGraphicsLadsAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class AccountSwitcherScreen121 extends Screen {
    private final Screen parent;
    private final AccountSwitcherScreen ui;

    public AccountSwitcherScreen121(Screen parent) {
        super(Component.literal("Account Switcher"));
        this.parent = parent;
        this.ui = new AccountSwitcherScreen();
        this.ui.setOnClose(this::onClose);
    }

    // Opaque common UI; 1.21.1 Screen.render would otherwise blur and cover it (as 1.21.11 and 26.x skip it).
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {}

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        ui.render(new GuiGraphicsLadsAdapter(guiGraphics, this.font), mouseX, mouseY);
        super.render(guiGraphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (ui.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
