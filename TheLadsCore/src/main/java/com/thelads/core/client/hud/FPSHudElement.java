package com.thelads.core.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class FPSHudElement extends HudElement {
    // "Update rate" -> how often the target value is re-sampled (ms).
    private static final long[] INTERVALS = { 0L, 150L, 400L, 1000L };

    private double displayed = 0;
    private double target = 0;
    private long lastSample = 0;

    public FPSHudElement() {
        this.x = 5;
        this.y = 5;
        this.width = 60;
        this.height = 16;
    }

    @Override
    public void render(GuiGraphicsExtractor g) {
        drawBackground(g);
        Minecraft mc = Minecraft.getInstance();
        int fps = mc.getFps();

        int rate = optCycle("Update rate", 1);
        boolean smooth = optBool("Smooth", true);
        long interval = INTERVALS[Math.max(0, Math.min(INTERVALS.length - 1, rate))];

        long now = System.currentTimeMillis();
        if (now - lastSample >= interval) {
            target = fps;
            lastSample = now;
        }
        // Smoothly ease the displayed value toward the target instead of jumping.
        displayed += (target - displayed) * (smooth ? 0.10 : 1.0);

        drawCenteredText(g, Math.round(displayed) + " FPS");
    }
}
