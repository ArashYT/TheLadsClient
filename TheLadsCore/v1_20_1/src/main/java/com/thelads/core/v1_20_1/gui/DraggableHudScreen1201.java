package com.thelads.core.v1_20_1.gui;

import com.thelads.core.client.gui.DraggableHudScreen;
import com.thelads.core.v1_20_1.adapter.GuiGraphicsLadsAdapter1201;
import com.thelads.core.v1_20_1.feature.HudEditorView1201;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class DraggableHudScreen1201 extends Screen {
    private final Screen parent;
    private final DraggableHudScreen ui;

    public DraggableHudScreen1201(Screen parent) {
        this(parent, new DraggableHudScreen());
    }

    public DraggableHudScreen1201(Screen parent, DraggableHudScreen controller) {
        super(Component.literal("Edit HUD"));
        this.parent = parent;
        this.ui = java.util.Objects.requireNonNull(controller);
        this.ui.setOnClose(this::onClose);
        this.ui.setOnSettings(name -> {
            var settings = new LadsSettingsScreen1201(this);
            settings.openModule(name);
            Minecraft.getInstance().setScreen(settings);
        });
    }

    @Override
    public void renderBackground(GuiGraphics g) {}

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        var adapter = new GuiGraphicsLadsAdapter1201(g, this.font) {
            @Override
            public boolean drawGameView(int x, int y, int width, int height) {
                return HudEditorView1201.draw(g, x, y, width, height);
            }
        };
        adapter.managed(() -> ui.render(adapter, mouseX, mouseY));
        super.render(g, mouseX, mouseY, delta);
    }

    /** The shared editor this screen draws and routes input to (QA reads its bounds and state). */
    public DraggableHudScreen ui() {
        return ui;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (ui.mouseClicked(mouseX, mouseY, button, modifiers())) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (ui.mouseReleased(mouseX, mouseY, button, modifiers())) {
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
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (ui.mouseScrolled(mouseX, mouseY, amount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ui.keyPressed(keyCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (ui.charTyped(codePoint)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    /** The held modifier keys in GLFW's bit order (shift 1, ctrl 2, alt 4), which the shared editor reads. */
    private static int modifiers() {
        return (Screen.hasShiftDown() ? 1 : 0) | (Screen.hasControlDown() ? 2 : 0) | (Screen.hasAltDown() ? 4 : 0);
    }

    @Override
    public void onClose() {
        ui.close();
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void removed() {
        ui.close();
        var adapter = new GuiGraphicsLadsAdapter1201(g, this.font) {
            @Override
            public boolean drawGameView(int x, int y, int width, int height) {
                return HudEditorView1201.draw(g, x, y, width, height);
            }
        };
        adapter.managed(() -> ui.render(adapter, mouseX, mouseY));
        super.removed();
    }
}
