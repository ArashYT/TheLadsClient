package com.thelads.core.v1_8_9.ladsapi.ui;

import com.thelads.core.client.hud.DialogOverlay;
import com.thelads.core.v1_8_9.adapter.GuiLadsAdapter;
import java.io.IOException;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

/**
 * The modal dialog screen (PROTOCOL.md section 22): opened over the bare game only, never over the pause menu or another screen.
 * Esc dismisses the modal (it does not open the pause menu); 1-6 choose; it closes by itself once no modal is left.
 */
public final class DialogScreen189 extends GuiScreen {
    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void drawScreen(int mx, int my, float partial) {
        DialogOverlay.render(new GuiLadsAdapter(fontRendererObj, width, height), mx, my);
    }

    @Override
    protected void keyTyped(char typed, int code) throws IOException {
        if (code == Keyboard.KEY_ESCAPE) {
            DialogOverlay.dismissModals();
            return;
        }
        if (code >= Keyboard.KEY_1 && code <= Keyboard.KEY_6) DialogOverlay.choose(code - Keyboard.KEY_1);
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) throws IOException {
        if (button == 0) DialogOverlay.click(mx, my);
    }

    @Override
    public void updateScreen() {
        if (DialogOverlay.modal() == null && mc != null) mc.displayGuiScreen(null);
    }
}
