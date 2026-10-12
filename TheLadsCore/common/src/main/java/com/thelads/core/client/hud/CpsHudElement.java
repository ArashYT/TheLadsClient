package com.thelads.core.client.hud;

import com.thelads.core.client.CpsTracker;
import com.thelads.core.client.bridge.LadsGraphics;

public class CpsHudElement extends TextHudElement {
    public CpsHudElement() {
        super(50);
        this.x = 10;
        this.y = 180;
        this.width = 65;
        this.height = 16;
    }

    private int lastShow = -1;
    private boolean lastLabel;
    private int lastLeft = -1;
    private int lastRight = -1;
    private String cachedText = "";

    @Override
    protected String updateText(LadsGraphics g) {
        int show = optCycle("Show", 0); // 0 = Both, 1 = Left, 2 = Right
        boolean label = optBool("Show label", true);

        int left = CpsTracker.get().leftCps();
        int right = CpsTracker.get().rightCps();

        if (show == lastShow && label == lastLabel && left == lastLeft && right == lastRight) {
            return cachedText;
        }
        lastShow = show;
        lastLabel = label;
        lastLeft = left;
        lastRight = right;

        if (show == 1) {
            cachedText = (label ? "CPS: " : "") + left;
        } else if (show == 2) {
            cachedText = (label ? "CPS: " : "") + right;
        } else {
            cachedText = (label ? "CPS: " : "") + left + " | " + right;
        }

        return cachedText;
    }
}
