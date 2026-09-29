package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;

public class DayHudElement extends TextHudElement {
    public DayHudElement() {
        super(50);
        this.x = 5;
        this.y = 165;
        this.width = 65;
        this.height = 16;
    }

    @Override
    protected String updateText(LadsGraphics g) {
        long day = g.getGame().getDayCount();
        boolean label = optBool("Show label", true);
        String text = (label ? "Day: " : "") + day;
        return text;
    }
}
