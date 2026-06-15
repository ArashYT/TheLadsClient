/*
 * Decompiled with CFR 0.152.
 */
package com.thelads.core.client.hud;

import com.thelads.core.client.hud.HudElement;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class MemoryHudElement
extends HudElement {
    public MemoryHudElement() {
        this.x = 5;
        this.y = 105;
        this.width = 120;
        this.height = 16;
    }

    @Override
    public void render(GuiGraphicsExtractor g) {
        this.drawBackground(g);
        Runtime rt = Runtime.getRuntime();
        long max = rt.maxMemory() / 0x100000L;
        long used = (rt.totalMemory() - rt.freeMemory()) / 0x100000L;
        int pct = max > 0L ? (int)(used * 100L / max) : 0;
        int mode = this.optCycle("Display", 1);
        String text = mode == 2 ? "Mem " + pct + "%" : (mode == 0 ? "Mem " + used + "/" + max + "MB" : "Mem " + used + "/" + max + "MB (" + pct + "%)");
        this.drawCenteredText(g, text);
    }
}

