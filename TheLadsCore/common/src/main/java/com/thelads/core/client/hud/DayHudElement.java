package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;

public class DayHudElement extends HudElement {
    public DayHudElement() {
        this.x = 5;
        this.y = 165;
        this.width = 65;
        this.height = 16;
    }

    @Override
    public void render(LadsGraphics g) {
        drawBackground(g);
        long day = g.getGame().getDayCount();
        boolean label = optBool("Show label", true);
        String text = (label ? "Day: " : "") + day;
        this.width = Math.max(50, g.textWidth(text) + 12);
        drawCenteredText(g, text);
    }
}
