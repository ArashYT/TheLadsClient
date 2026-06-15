package com.thelads.core.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class SpeedHudElement extends HudElement {
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
        drawBackground(g);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        long now = System.nanoTime();
        double px = mc.player.getX();
        double pz = mc.player.getZ();
        if (lastTime != 0L) {
            double dt = (now - lastTime) / 1.0e9;
            if (dt > 0.0) {
                double dx = px - lastX;
                double dz = pz - lastZ;
                bps = bps * 0.8 + Math.sqrt(dx * dx + dz * dz) / dt * 0.2;
            }
        }
        lastX = px;
        lastZ = pz;
        lastTime = now;

        int unit      = optCycle("Unit", 0);       // 0=b/s, 1=km/h
        int precision = optCycle("Precision", 1);  // 0=0dec, 1=1dec, 2=2dec

        double value = unit == 1 ? bps * 3.6 : bps;
        String unitLabel = unit == 1 ? " km/h" : " b/s";
        String fmt = "%." + precision + "f";
        drawCenteredText(g, String.format(fmt, value) + unitLabel);
    }
}
