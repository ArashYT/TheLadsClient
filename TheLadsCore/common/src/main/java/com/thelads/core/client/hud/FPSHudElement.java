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
        // 10% of the way each time it is drawn; a capped build stands for several draws (HudFrameCap.steps), so it keeps its pace.
        displayed += (target - displayed) * (smooth ? 1 - Math.pow(0.9, HudFrameCap.steps()) : 1.0);

        int mode = optCycle("Display", 0);
        int gameVal = (int) Math.round(displayed);
        int hudVal = HudManager.getInstance().getMeasuredHudFps();
        if (mode == 1) return hudVal + " HUD FPS";
        if (mode == 2) return gameVal + " FPS · " + hudVal + " HUD";
        return gameVal + " FPS";
    }
}
