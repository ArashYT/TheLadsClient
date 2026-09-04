package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;

public class DirectionHudElement extends HudElement {
    public DirectionHudElement() {
        this.x = 5;
        this.y = 125;
        this.width = 80;
        this.height = 16;
    }

    @Override
    public void render(LadsGraphics g) {
        drawBackground(g);
        String dir = g.getGame().getPlayerDirection();
        String text = (dir != null) ? dir : "North";
        this.width = Math.max(60, g.textWidth(text) + 12);
        drawCenteredText(g, text);
    }
}
