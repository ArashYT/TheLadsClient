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

    private long lastUsed = -1;
    private long lastMax = -1;
    private int lastMode = -1;
    private String cachedText = "";

    @Override
    protected String updateText(LadsGraphics g) {
        long used = g.getGame().getUsedMemoryMb();
        long max = g.getGame().getMaxMemoryMb();
        int mode = optCycle("Display", 1); // 0 = Used / Max, 1 = Used / Max + %, 2 = Percent only

        if (used == lastUsed && max == lastMax && mode == lastMode) {
            return cachedText;
        }
        lastUsed = used;
        lastMax = max;
        lastMode = mode;

        long pct = max > 0 ? (used * 100 / max) : 0;
        if (mode == 2) {
            cachedText = "Mem: " + pct + "%";
        } else if (mode == 0) {
            cachedText = used + "/" + max + "MB";
        } else {
            cachedText = used + "/" + max + "MB (" + pct + "%)";
        }

        return cachedText;
    }
}
