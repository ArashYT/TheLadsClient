package com.thelads.core.v1_8_9.gui;

import net.minecraft.client.gui.GuiButton;

/** Implemented by the pause menu through GuiIngameMenuMixin: its "Lads Client" and Multiplayer buttons (QA finds and clicks them). */
public interface LadsPauseButton {
    GuiButton ladsButton();
    GuiButton ladsMultiplayerButton();
}
