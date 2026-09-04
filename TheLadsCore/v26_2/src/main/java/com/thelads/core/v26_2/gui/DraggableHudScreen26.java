package com.thelads.core.v26_2.gui;

import com.thelads.core.client.gui.DraggableHudScreen;
import com.thelads.core.v26_2.adapter.GuiGraphicsExtractorLadsAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class DraggableHudScreen26 extends Screen {
    private final Screen parent;
    private final DraggableHudScreen ui;

    public DraggableHudScreen26(Screen parent) {
        super(Component.literal("Edit HUD"));
        this.parent = parent;
        this.ui = new DraggableHudScreen();
        this.ui.setOnClose(this::onClose);
    }

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
        if (ui.keyPressed(event.key())) {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }
}
