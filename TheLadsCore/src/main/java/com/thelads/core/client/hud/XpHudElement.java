package com.thelads.core.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class XpHudElement extends HudElement {
    public XpHudElement() {
        this.x = 5;
        this.y = 245;
        this.width = 80;
        this.height = 16;
    }

    @Override
    public void render(GuiGraphicsExtractor g) {
        drawBackground(g);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int level   = mc.player.experienceLevel;
        int pct     = Math.round(mc.player.experienceProgress * 100);
        int format  = optCycle("Format", 0); // 0=Level, 1=Progress%, 2=Both

        String text;
        switch (format) {
            case 1  -> text = pct + "%";
            case 2  -> text = "Lvl " + level + " · " + pct + "%";
            default -> text = "Lvl " + level;
        }
        drawCenteredText(g, text);
    }
}
