package com.thelads.core.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class DayHudElement extends HudElement {
    public DayHudElement() {
        this.x = 5;
        this.y = 165;
        this.width = 70;
        this.height = 16;
    }

    @Override
    public void render(GuiGraphicsExtractor g) {
        drawBackground(g);
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long day = mc.level.getOverworldClockTime() / 24000L;
        boolean showLabel = optBool("Show label", true);
        drawCenteredText(g, showLabel ? "Day " + day : String.valueOf(day));
    }
}
