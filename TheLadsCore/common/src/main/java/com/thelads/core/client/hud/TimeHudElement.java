package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;

public class TimeHudElement extends HudElement {
    public TimeHudElement() {
        this.x = 5;
        this.y = 185;
        this.width = 65;
        this.height = 16;
    }

    @Override
    public void render(LadsGraphics g) {
        drawBackground(g);
        String time = g.getGame().getGameTime();
        boolean label = optBool("Show label", false);
        String text = (label ? "Time: " : "") + (time != null ? time : "12:00");
        this.width = Math.max(50, g.textWidth(text) + 12);
        drawCenteredText(g, text);
    }
}
