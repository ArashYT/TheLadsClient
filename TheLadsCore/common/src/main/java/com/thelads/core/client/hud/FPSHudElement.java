package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;

public class FPSHudElement extends TextHudElement {
    private static final long[] INTERVALS = { 0L, 150L, 400L, 1000L };

    private double displayed = 0;
    private double target = 0;
    private long lastSample = 0;

    public FPSHudElement() {
        super(60);
        this.x = 5;
        this.y = 5;
        this.width = 60;
        this.height = 16;
    }

    @Override
    protected String updateText(LadsGraphics g) {
        int fps = g.getGame().getFps();

        int rate = optCycle("Update rate", 1);
        boolean smooth = optBool("Smooth", true);
        long interval = INTERVALS[Math.max(0, Math.min(INTERVALS.length - 1, rate))];

        long now = System.currentTimeMillis();
        if (now - lastSample >= interval) {
            target = fps;
            lastSample = now;
        }
        displayed += (target - displayed) * (smooth ? 0.10 : 1.0);

        return Math.round(displayed) + " FPS";
    }
}
