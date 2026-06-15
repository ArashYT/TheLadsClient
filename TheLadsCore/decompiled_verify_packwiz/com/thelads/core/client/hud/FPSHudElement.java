/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 */
package com.thelads.core.client.hud;

import com.thelads.core.client.hud.HudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class FPSHudElement
extends HudElement {
    private static final long[] INTERVALS = new long[]{0L, 150L, 400L, 1000L};
    private double displayed = 0.0;
    private double target = 0.0;
    private long lastSample = 0L;

    public FPSHudElement() {
        this.x = 5;
        this.y = 5;
        this.width = 60;
        this.height = 16;
    }

    @Override
    public void render(GuiGraphicsExtractor g) {
        this.drawBackground(g);
        Minecraft mc = Minecraft.getInstance();
        int fps = mc.getFps();
        int rate = this.optCycle("Update rate", 1);
        boolean smooth = this.optBool("Smooth", true);
        long interval = INTERVALS[Math.max(0, Math.min(INTERVALS.length - 1, rate))];
        long now = System.currentTimeMillis();
        if (now - this.lastSample >= interval) {
            this.target = fps;
            this.lastSample = now;
        }
        this.displayed += (this.target - this.displayed) * (smooth ? 0.1 : 1.0);
        this.drawCenteredText(g, Math.round(this.displayed) + " FPS");
    }
}

