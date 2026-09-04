package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;

public class ScoreboardHudElement extends HudElement {
    public ScoreboardHudElement() {
        this.x = 200;
        this.y = 80;
        this.width = 120;
        this.height = 100;
    }

    @Override
    public void render(LadsGraphics g) {
        // Draw placeholder bounding box in HUD editor
        drawBackground(g);
        g.fill(x, y, x + width, y + 1, 0x886C63FF);
        g.fill(x, y + height - 1, x + width, y + height, 0x886C63FF);
        g.fill(x, y, x + 1, y + height, 0x886C63FF);
        g.fill(x + width - 1, y, x + width, y + height, 0x886C63FF);
        drawCenteredText(g, "SCOREBOARD");
    }
}
