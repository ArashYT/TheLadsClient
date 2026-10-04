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
        this(parent, new DraggableHudScreen());
    }

    public DraggableHudScreen26(Screen parent, DraggableHudScreen controller) {
        super(Component.literal("Edit HUD"));
        this.parent = parent;
        this.ui = java.util.Objects.requireNonNull(controller);
        this.ui.setOnClose(this::onClose);
        this.ui.setOnSettings(name -> {
            var settings = new LadsSettingsScreen26(this);
            settings.openModule(name);
            Minecraft.getInstance().setScreenAndShow(settings);
        });
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {}

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        ui.render(new GuiGraphicsExtractorLadsAdapter(g, this.font) {
            @Override public boolean drawGameView(int x, int y, int width, int height) {
                return com.thelads.core.v26_2.feature.HudEditorView.draw(g, x, y, width, height);
            }
        }, mouseX, mouseY);
        super.extractRenderState(g, mouseX, mouseY, delta);
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
        if (ui.mouseReleased(event.x(), event.y(), event.button(), event.modifiers())) {
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
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return ui.mouseScrolled(mouseX, mouseY, verticalAmount) || super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
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
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override public boolean charTyped(net.minecraft.client.input.CharacterEvent event){return ui.charTyped(event.codepoint())||super.charTyped(event);}

    @Override public void removed() { ui.close(); com.thelads.core.v26_2.feature.HudEditorView.release(); super.removed(); }
}
