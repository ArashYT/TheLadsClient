package com.thelads.core.v1_8_9.gui;

import net.minecraft.client.gui.GuiButton;

/** Implemented by every GuiScreen through GuiScreenMixin: presses one of its buttons as a click on it would (More screens). */
public interface LadsButtonPress {
    void ladsPress(GuiButton button);
}
