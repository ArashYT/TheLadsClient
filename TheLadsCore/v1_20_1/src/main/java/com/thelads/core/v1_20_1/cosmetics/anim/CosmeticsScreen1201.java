package com.thelads.core.v1_20_1.cosmetics.anim;

import com.thelads.core.client.cosmetics.CosmeticsScreen;
import com.thelads.core.v1_20_1.adapter.GuiGraphicsLadsAdapter1201;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Host screen for the version-free CosmeticsScreen on 1.20.1. Shows wheel slots, sharing toggles,
 * and per-emote keybinds via vanilla Controls.
 */
public final class CosmeticsScreen1201 extends Screen {
    private final Screen parent;
    private final CosmeticsScreen ui;
    private boolean closed;

    public CosmeticsScreen1201(Screen parent) {
        super(Component.literal("Cosmetics"));
        this.parent = parent;
        this.ui = new CosmeticsScreen(this::keyLabel, this::onControls, this::returnToParent);
    }

    public CosmeticsScreen ui() {
        return ui;
    }

    private String keyLabel(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null && mc.options.keyMappings != null) {
            for (KeyMapping mapping : mc.options.keyMappings) {
                if (mapping.getName().equals(name)) {
                    return mapping.isUnbound() ? "" : mapping.getTranslatedKeyMessage().getString();
                }
            }
        }
        return "";
    }

    private void onControls() {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new KeyBindsScreen(this, mc.options));
    }

    private void returnToParent() {
        if (closed) return;
        closed = true;
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return parent == null || parent.isPauseScreen();
    }

    @Override
    public void renderBackground(GuiGraphics g) {
        // The Lads UI paints the full background
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        ui.render(new GuiGraphicsLadsAdapter1201(g), mx, my);
        super.render(g, mx, my, delta);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (ui.mouseClicked(mx, my, button)) return true;
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            ui.close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        ui.close();
    }
}
