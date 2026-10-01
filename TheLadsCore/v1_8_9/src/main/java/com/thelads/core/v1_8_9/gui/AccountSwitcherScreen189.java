package com.thelads.core.v1_8_9.gui;

import com.thelads.core.client.auth.AccountSwitcherScreen;
import com.thelads.core.v1_8_9.adapter.GuiLadsAdapter;
import java.io.IOException;
import net.minecraft.client.gui.GuiScreen;

/** The shared account switcher on 1.8.9 (AccountSwitcherScreen121 on the other versions). */
public class AccountSwitcherScreen189 extends GuiScreen {
    private final GuiScreen parent;
    private final AccountSwitcherScreen ui = new AccountSwitcherScreen();

    public AccountSwitcherScreen189(GuiScreen parent) {
        this.parent = parent;
        ui.setOnClose(() -> mc.displayGuiScreen(parent));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        ui.render(new GuiLadsAdapter(fontRendererObj, width, height), mouseX, mouseY);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        if (!ui.mouseClicked(mouseX, mouseY, button)) super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1) mc.displayGuiScreen(parent);
    }
}
