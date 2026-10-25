package com.thelads.core.v1_20_1.social;

import com.thelads.core.client.social.ui.HostScreenLogic;
import com.thelads.core.v1_20_1.adapter.GuiGraphicsLadsAdapter1201;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;

/** Multiplayer Options (replaces Open to LAN) on 1.20.1: host screen for the common HostScreenLogic, forwards render and input only. */
public final class HostScreen1201 extends Screen {
    private final HostScreenLogic ui;

    public HostScreen1201(Screen parent) {
        super(Component.literal("Multiplayer Options"));
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        this.ui = HostScreenLogic.forWorld(server == null ? null : server.getWorldPath(LevelResource.ROOT),
            () -> Minecraft.getInstance().setScreen(parent));
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
}
