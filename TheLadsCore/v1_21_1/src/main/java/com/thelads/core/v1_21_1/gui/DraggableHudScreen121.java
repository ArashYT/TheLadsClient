package com.thelads.core.v1_21_1.gui;

import com.thelads.core.client.gui.DraggableHudScreen;
import com.thelads.core.v1_21_1.adapter.GuiGraphicsLadsAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class DraggableHudScreen121 extends Screen {
    private final Screen parent;
    private final DraggableHudScreen ui;

    public DraggableHudScreen121(Screen parent) {
        super(Component.literal("Edit HUD"));
        this.parent = parent;
        this.ui = new DraggableHudScreen();
        this.ui.setOnClose(this::onClose);
        this.ui.setOnSettings(name -> {
            var settings = new LadsSettingsScreen121(this);
            settings.openModule(name);
            Minecraft.getInstance().setScreen(settings);
        });
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {}

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        ui.render(new GuiGraphicsLadsAdapter(guiGraphics, this.font), mouseX, mouseY);
        super.render(guiGraphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (ui.mouseClicked(mouseX, mouseY, button, (Screen.hasShiftDown() ? 1 : 0) | (Screen.hasControlDown() ? 2 : 0))) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (ui.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (ui.mouseDragged(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ui.keyPressed(keyCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        ui.close();
        Minecraft.getInstance().setScreen(parent);
    }

    @Override public boolean charTyped(char character,int modifiers) {return ui.charTyped(character) || super.charTyped(character,modifiers);}

    @Override public void removed() { ui.close(); super.removed(); }
}
