package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;
import java.util.List;

public class TexturePackHudElement extends HudElement {
    public TexturePackHudElement() {
        this.x = 10;
        this.y = 200;
        this.width = 110;
        this.height = 16;
    }

    @Override
    public void render(LadsGraphics g) {
        List<String> packs = g.getGame().getActiveResourcePacks();
        String current = packs.isEmpty() ? "Default" : packs.get(0);
        String text = "Pack: " + current;
        this.width = Math.max(80, g.textWidth(text) + 12);
        drawBackground(g);
        drawCenteredText(g, text);
    }
}
