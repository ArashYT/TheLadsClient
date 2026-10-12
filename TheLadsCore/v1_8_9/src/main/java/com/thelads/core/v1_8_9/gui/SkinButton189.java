package com.thelads.core.v1_8_9.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

/** A button of the Skin screens: the Lads surface every button has, with their soft pink label (LadsButton1201 and LadsButton). */
final class SkinButton189 extends GuiButton {
    SkinButton189(int id, int x, int y, int width, int height, String label) {
        super(id, x, y, width, height, label);
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        packedFGColour = enabled ? 0xFFFCECF3 : 0xFF86717B;
        super.drawButton(mc, mouseX, mouseY);
    }
}
