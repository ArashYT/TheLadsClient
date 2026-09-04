package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;
import java.util.List;

public class PotionHudElement extends HudElement {
    public PotionHudElement() {
        this.x = 10;
        this.y = 220;
        this.width = 90;
        this.height = 16;
    }

    @Override
    public void render(LadsGraphics g) {
        List<String> effects = g.getGame().getActivePotionEffects();
        if (effects.isEmpty()) {
            if (optBool("Show when empty", false)) {
                this.width = 90;
                this.height = 16;
                drawBackground(g);
                drawCenteredText(g, "No Effects");
            }
            return;
        }

        int lineH = g.fontHeight() + 3;
        this.height = effects.size() * lineH + 4;
        int maxW = 80;
        for (String eff : effects) {
            maxW = Math.max(maxW, g.textWidth(eff) + 8);
        }
        this.width = maxW;
        drawBackground(g);

        int ty = y + 2;
        int color = resolveColor();
        for (String eff : effects) {
            g.drawText(eff, x + 4, ty, color);
            ty += lineH;
        }
    }
}
