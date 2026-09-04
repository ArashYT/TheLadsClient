package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;

public class HungerHudElement extends HudElement {
    public HungerHudElement() {
        this.x = 5;
        this.y = 225;
        this.width = 65;
        this.height = 16;
    }

    @Override
    public void render(LadsGraphics g) {
        drawBackground(g);
        int food = g.getGame().getFoodLevel();
        boolean label = optBool("Show label", true);
        String text = (label ? "Food: " : "") + food + "/20";
        this.width = Math.max(50, g.textWidth(text) + 12);
        drawCenteredText(g, text);
    }
}
