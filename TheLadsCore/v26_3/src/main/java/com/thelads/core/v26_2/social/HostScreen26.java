package com.thelads.core.v26_2.social;

import com.thelads.core.client.social.ui.HostScreenLogic;
import com.thelads.core.v26_2.adapter.GuiGraphicsExtractorLadsAdapter;
import com.thelads.core.v26_2.gui.CommonInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;

/** Multiplayer Options (replaces Open to LAN) on 26.3: host screen for the common HostScreenLogic, forwards render and input only. */
public final class HostScreen26 extends Screen {
    private final HostScreenLogic ui;

    public HostScreen26(Screen parent) {
        super(Component.literal("Multiplayer Options"));
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        this.ui = HostScreenLogic.forWorld(server == null ? null : server.getWorldPath(LevelResource.ROOT),
            () -> Minecraft.getInstance().setScreenAndShow(parent));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
        // HostScreenLogic paints the full background.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        ui.render(new GuiGraphicsExtractorLadsAdapter(g, font), mx, my);
        super.extractRenderState(g, mx, my, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (ui.mouseClicked(event.x(), event.y(), CommonInput.button(event.button()))) return true;
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (ui.mouseScrolled(mouseX, mouseY, verticalAmount)) return true;
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (ui.keyPressed(CommonInput.key(event), 0, CommonInput.modifiers(event.modifiers()))) return true;
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (ui.charTyped((char) event.codepoint())) return true;
        return super.charTyped(event);
    }

    @Override
    public void onClose() {
        ui.close();
    }
}
