package com.thelads.core.v1_20_1.ladsapi.ui;

import com.thelads.core.client.hud.DialogOverlay;
import com.thelads.core.v1_20_1.adapter.GuiGraphicsLadsAdapter1201;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** A server modal (Lads API section 21): takes the mouse, Esc dismisses, closes by itself when the modal is gone. Number keys: InputStampMixin1201. */
public final class DialogScreen1201 extends Screen {
    public DialogScreen1201() {
        super(Component.literal("Server"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        // the overlay dims the world itself
        DialogOverlay.render(new GuiGraphicsLadsAdapter1201(g, this.font), mx, my);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int mods) {
        if (key == 256) { // Esc
            DialogOverlay.dismissModals();
            return true;
        }
        return super.keyPressed(key, scanCode, mods);
    }

    @Override
    public void tick() {
        super.tick();
        if (DialogOverlay.modal() == null && this.minecraft != null) this.minecraft.setScreen(null);
    }
}
