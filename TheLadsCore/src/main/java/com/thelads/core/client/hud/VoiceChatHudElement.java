package com.thelads.core.client.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public class VoiceChatHudElement extends HudElement {
    public VoiceChatHudElement() {
        this.x = 0;
        this.y = 0;
        this.width = 100;
        this.height = 32; // Dummy preview size
    }

    @Override
    public void render(GuiGraphicsExtractor g) {
        // Draw dummy preview only when in HUD editor screen
        if (net.minecraft.client.Minecraft.getInstance().screen instanceof com.thelads.core.client.gui.DraggableHudScreen) {
            drawBackground(g);
            drawCenteredText(g, "Voice Chat");
        }
    }
}
