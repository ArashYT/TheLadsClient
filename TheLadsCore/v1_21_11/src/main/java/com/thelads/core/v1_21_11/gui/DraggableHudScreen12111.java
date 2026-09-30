package com.thelads.core.v1_21_11.gui;

import com.thelads.core.client.gui.DraggableHudScreen;
import com.thelads.core.v1_21_11.adapter.GuiGraphicsLadsAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class DraggableHudScreen12111 extends Screen {
    private final Screen parent;
    private final DraggableHudScreen ui;

    public DraggableHudScreen12111(Screen parent) {
        this(parent, new DraggableHudScreen());
    }

    /** As on 26.x: QA passes an editor whose persistence it owns. */
    public DraggableHudScreen12111(Screen parent, DraggableHudScreen controller) {
        super(Component.literal("Edit HUD"));
        this.parent = parent;
        this.ui = java.util.Objects.requireNonNull(controller);
        this.ui.setOnClose(this::onClose);
        this.ui.setOnSettings(name -> {
            var settings = new LadsSettingsScreen12111(this);
            settings.openModule(name);
            Minecraft.getInstance().setScreen(settings);
        });
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
        if (ui.mouseClicked(event.x(), event.y(), event.button(), event.modifiers())) {
            return true;
        }
        return super.mouseClicked(event, isDouble);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (ui.mouseReleased(event.x(), event.y(), event.button())) {
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (ui.mouseDragged(event.x(), event.y(), event.button())) {
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (ui.keyPressed(event.key(), event.modifiers())) {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        ui.close();
        Minecraft.getInstance().setScreen(parent);
    }

    @Override public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {return ui.charTyped(event.codepoint()) || super.charTyped(event);}

    @Override public void removed() { ui.close(); super.removed(); }
}
