package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;

public class PaperdollHudElement extends HudElement {
    public PaperdollHudElement() {
        this.x = 20;
        this.y = 20;
        this.width = 30;
        this.height = 45;
    }

    @Override
    public void render(LadsGraphics g) {
        drawBackground(g);
        // Draw miniature player doll frame or preview
        g.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0x33FFFFFF);
        int color = resolveColor();
        // Head preview
        g.fill(x + width / 2 - 4, y + 4, x + width / 2 + 4, y + 12, color);
        // Body preview
        g.fill(x + width / 2 - 6, y + 13, x + width / 2 + 6, y + 27, color);
        // Legs preview
        g.fill(x + width / 2 - 5, y + 28, x + width / 2 - 1, y + 40, color);
        g.fill(x + width / 2 + 1, y + 28, x + width / 2 + 5, y + 40, color);
    }
}
