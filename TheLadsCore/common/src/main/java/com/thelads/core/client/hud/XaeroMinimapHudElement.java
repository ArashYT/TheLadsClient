package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;

public class XaeroMinimapHudElement extends HudElement {
    public XaeroMinimapHudElement() {
        this.x = 5;
        this.y = 5;
        this.width = 70;
        this.height = 70;
    }

    @Override
    public void render(LadsGraphics g) {
        drawBackground(g);
        // Subtle outline and compass points
        g.fill(x, y, x + width, y + 1, 0x88FFFFFF);
        g.fill(x, y + height - 1, x + width, y + height, 0x88FFFFFF);
        g.fill(x, y, x + 1, y + height, 0x88FFFFFF);
        g.fill(x + width - 1, y, x + width, y + height, 0x88FFFFFF);
        drawCenteredText(g, "MINIMAP");
    }
}
