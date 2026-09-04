package com.thelads.core.client.hud;

import com.thelads.core.client.CpsTracker;
import com.thelads.core.client.bridge.LadsGraphics;

public class CpsHudElement extends HudElement {
    public CpsHudElement() {
        this.x = 10;
        this.y = 180;
        this.width = 65;
        this.height = 16;
    }

    @Override
    public void render(LadsGraphics g) {
        drawBackground(g);
        int show = optCycle("Show", 0); // 0 = Both, 1 = Left, 2 = Right
        boolean label = optBool("Show label", true);

        int left = CpsTracker.get().leftCps();
        int right = CpsTracker.get().rightCps();

        String text;
        if (show == 1) {
            text = (label ? "CPS: " : "") + left;
        } else if (show == 2) {
            text = (label ? "CPS: " : "") + right;
        } else {
            text = (label ? "CPS: " : "") + left + " | " + right;
        }

        this.width = Math.max(50, g.textWidth(text) + 12);
        drawCenteredText(g, text);
    }
}
