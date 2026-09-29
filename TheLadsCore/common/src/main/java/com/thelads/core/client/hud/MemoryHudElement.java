package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;

public class MemoryHudElement extends TextHudElement {
    public MemoryHudElement() {
        super(70);
        this.x = 5;
        this.y = 105;
        this.width = 95;
        this.height = 16;
    }

    @Override
    protected String updateText(LadsGraphics g) {
        long used = g.getGame().getUsedMemoryMb();
        long max = g.getGame().getMaxMemoryMb();
        long pct = max > 0 ? (used * 100 / max) : 0;

        int mode = optCycle("Display", 1); // 0 = Used / Max, 1 = Used / Max + %, 2 = Percent only
        String text;
        if (mode == 2) {
            text = "Mem: " + pct + "%";
        } else if (mode == 0) {
            text = used + "/" + max + "MB";
        } else {
            text = used + "/" + max + "MB (" + pct + "%)";
        }

        return text;
    }
}
