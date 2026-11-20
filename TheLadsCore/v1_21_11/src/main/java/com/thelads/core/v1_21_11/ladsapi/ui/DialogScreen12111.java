package com.thelads.core.v1_21_11.ladsapi.ui;

import com.thelads.core.client.hud.DialogOverlay;
import com.thelads.core.v1_21_11.adapter.GuiGraphicsLadsAdapter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/** A server modal (Lads API section 21): takes the mouse, Esc dismisses, closes by itself when the modal is gone. Number keys: InputStampMixin. */
public final class DialogScreen12111 extends Screen {
    public DialogScreen12111() {
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
    public void renderBackground(GuiGraphics g, int mx, int my, float delta) {
        // the overlay dims the world itself
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        DialogOverlay.render(new GuiGraphicsLadsAdapter(g, this.font), mx, my);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256) { // Esc
            DialogOverlay.dismissModals();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void tick() {
        super.tick();
        if (DialogOverlay.modal() == null && this.minecraft != null) this.minecraft.setScreen(null);
    }
}
