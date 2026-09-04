package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;

public class HealthHudElement extends HudElement {
    public HealthHudElement() {
        this.x = 5;
        this.y = 205;
        this.width = 75;
        this.height = 16;
    }

    @Override
    public void render(LadsGraphics g) {
        drawBackground(g);
        float hp = g.getGame().getHealth();
        float max = g.getGame().getMaxHealth();
        int fmt = optCycle("Format", 0);

        String text;
        if (fmt == 3) {
            text = (int)(max > 0 ? (hp * 100 / max) : 0) + "%";
        } else if (fmt == 2) {
            text = String.valueOf((int) Math.ceil(hp));
        } else if (fmt == 1) {
            text = "HP " + (int) Math.ceil(hp) + "/" + (int) Math.ceil(max);
        } else {
            text = (int) Math.ceil(hp) + "/" + (int) Math.ceil(max);
        }

        this.width = Math.max(50, g.textWidth(text) + 12);
        drawCenteredText(g, text);
    }
}
