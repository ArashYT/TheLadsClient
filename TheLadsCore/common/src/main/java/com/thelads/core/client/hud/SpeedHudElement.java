package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;

public class SpeedHudElement extends HudElement {
    public SpeedHudElement() {
        this.x = 5;
        this.y = 145;
        this.width = 75;
        this.height = 16;
    }

    @Override
    public void render(LadsGraphics g) {
        drawBackground(g);
        double spd = g.getGame().getSpeed();
        int unit = optCycle("Unit", 0); // 0 = b/s, 1 = km/h
        double val = (unit == 1) ? spd * 3.6 : spd;
        String uStr = (unit == 1) ? " km/h" : " b/s";
        String text = String.format("%.1f%s", val, uStr);
        this.width = Math.max(60, g.textWidth(text) + 12);
        drawCenteredText(g, text);
    }
}
