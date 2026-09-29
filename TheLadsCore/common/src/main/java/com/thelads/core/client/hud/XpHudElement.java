package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;

public class XpHudElement extends TextHudElement {
    public XpHudElement() {
        super(45);
        this.x = 5;
        this.y = 245;
        this.width = 65;
        this.height = 16;
    }

    @Override
    protected String updateText(LadsGraphics g) {
        int lvl = g.getGame().getXpLevel();
        float prog = g.getGame().getXpProgress();
        int fmt = optCycle("Format", 0);

        String text;
        if (fmt == 1) {
            text = (int)(prog * 100) + "%";
        } else if (fmt == 2) {
            text = "Lvl " + lvl + " (" + (int)(prog * 100) + "%)";
        } else {
            text = "Lvl " + lvl;
        }

        return text;
    }
}
