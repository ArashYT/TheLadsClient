package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;

public class ArmorHudElement extends HudElement {
    public ArmorHudElement() {
        this.x = 5;
        this.y = 85;
        this.width = 80;
        this.height = 16;
    }

    @Override
    public void render(LadsGraphics g) {
        drawBackground(g);
        drawCenteredText(g, "Armor: 100%");
    }
}
