package com.thelads.core.client.hud;

import com.thelads.core.client.CpsTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class CpsHudElement extends HudElement {
    public CpsHudElement() {
        this.x = 5;
        this.y = 265;
        this.width = 110;
        this.height = 16;
    }

    @Override
    public void render(GuiGraphicsExtractor g) {
        drawBackground(g);
        int l         = CpsTracker.get().leftCps();
        int r         = CpsTracker.get().rightCps();
        int show      = optCycle("Show", 0);       // 0=Both, 1=Left only, 2=Right only
        boolean label = optBool("Show label", true);

        String text;
        if (show == 1) {
            text = label ? "L " + l + " CPS" : String.valueOf(l);
        } else if (show == 2) {
            text = label ? "R " + r + " CPS" : String.valueOf(r);
        } else {
            text = label ? "L " + l + "  R " + r + "  CPS" : l + " / " + r;
        }
        drawCenteredText(g, text);
    }
}
