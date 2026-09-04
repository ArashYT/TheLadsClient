package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;

public class BiomeHudElement extends HudElement {
    public BiomeHudElement() {
        this.x = 5;
        this.y = 45;
        this.width = 90;
        this.height = 16;
    }

    @Override
    public void render(LadsGraphics g) {
        String biome = g.getGame().getBiomeName();
        boolean label = optBool("Show label", false);
        String text = (label ? "Biome: " : "") + (biome != null ? biome : "Unknown");

        this.width = Math.max(70, g.textWidth(text) + 12);
        drawBackground(g);
        drawCenteredText(g, text);
    }
}
