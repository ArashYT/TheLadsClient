package com.thelads.core.v1_8_9.gui;

import net.minecraft.client.Minecraft;

/** GuiButton's Lads look (GuiButtonMixin), callable for the subclasses that override drawButton (GuiButtonExtMixin). */
public interface LadsButtonDraw189 {
    void ladsDraw(Minecraft mc, int mouseX, int mouseY);
}
