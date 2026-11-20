package com.thelads.core.v26_2.ladsapi.ui;

import com.thelads.core.client.hud.DialogOverlay;
import com.thelads.core.v26_2.adapter.GuiGraphicsExtractorLadsAdapter;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/** A server modal (Lads API section 21): takes the mouse, Esc dismisses, closes by itself when the modal is gone. Number keys: InputStampMixin. */
public final class DialogScreen26 extends Screen {
    public DialogScreen26() {
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
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
        // the overlay dims the world itself
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        DialogOverlay.render(new GuiGraphicsExtractorLadsAdapter(g, this.font), mx, my);
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
        if (DialogOverlay.modal() == null && this.minecraft != null) this.minecraft.setScreenAndShow(null);
    }
}
