package com.thelads.core.client.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public class MemoryHudElement extends HudElement {
    public MemoryHudElement() {
        this.x = 5;
        this.y = 105;
        this.width = 120;
        this.height = 16;
    }

    @Override
    public void render(GuiGraphicsExtractor g) {
        drawBackground(g);
        Runtime rt = Runtime.getRuntime();
        long max = rt.maxMemory() / 1048576L;
        long used = (rt.totalMemory() - rt.freeMemory()) / 1048576L;
        int pct = max > 0 ? (int) (used * 100L / max) : 0;
        // "Display" option: 0 = Used/Max, 1 = Used/Max + %, 2 = Percent only
        int mode = optCycle("Display", 1);
        String text;
        if (mode == 2) {
            text = "Mem " + pct + "%";
        } else if (mode == 0) {
            text = "Mem " + used + "/" + max + "MB";
        } else {
            text = "Mem " + used + "/" + max + "MB (" + pct + "%)";
        }
        drawCenteredText(g, text);
    }
}
