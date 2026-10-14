package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;

public class MemoryHudElement extends TextHudElement {
    private long lastUpdateMs = 0;
    private long lastUsed = -1;
    private long lastMax = -1;
    private int lastMode = -1;
    private String cachedText = "";

    public MemoryHudElement() {
        super(70);
        this.x = 5;
        this.y = 105;
        this.width = 95;
        this.height = 16;
    }

    @Override
    protected String updateText(LadsGraphics g) {
        int mode = optCycle("Display", 1); // 0 = Used / Max, 1 = Used / Max + %, 2 = Percent only
        long now = System.currentTimeMillis();
        if (!editor && mode == lastMode && (now - lastUpdateMs < 250L) && cachedText != null && !cachedText.isEmpty()) {
            return cachedText;
        }
        lastUpdateMs = now;
        long used = g.getGame().getUsedMemoryMb();
        long max = g.getGame().getMaxMemoryMb();
        if (used == lastUsed && max == lastMax && mode == lastMode && cachedText != null && !cachedText.isEmpty()) {
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
