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

public class SpeedHudElement
extends HudElement {
    private double lastX;
    private double lastZ;
    private long lastTime = 0L;
    private double bps = 0.0;

    public SpeedHudElement() {
        this.x = 5;
        this.y = 145;
        this.width = 90;
        this.height = 16;
    }

    @Override
    public void render(GuiGraphicsExtractor g) {
        double dt;
        this.drawBackground(g);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        long now = System.nanoTime();
        double px = mc.player.getX();
        double pz = mc.player.getZ();
        if (this.lastTime != 0L && (dt = (double)(now - this.lastTime) / 1.0E9) > 0.0) {
            double dx = px - this.lastX;
            double dz = pz - this.lastZ;
            this.bps = this.bps * 0.8 + Math.sqrt(dx * dx + dz * dz) / dt * 0.2;
        }
        this.lastX = px;
        this.lastZ = pz;
        this.lastTime = now;
        int unit = this.optCycle("Unit", 0);
        int precision = this.optCycle("Precision", 1);
        double value = unit == 1 ? this.bps * 3.6 : this.bps;
        String unitLabel = unit == 1 ? " km/h" : " b/s";
        String fmt = "%." + precision + "f";
        this.drawCenteredText(g, String.format(fmt, value) + unitLabel);
    }
}

