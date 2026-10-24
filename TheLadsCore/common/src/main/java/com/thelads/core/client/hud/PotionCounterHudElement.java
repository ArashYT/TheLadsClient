package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;

public class PotionCounterHudElement extends TextHudElement {
    private int lastPots = -1;
    private int lastSoups = -1;
    private int lastDisplay = -1;
    private String cachedText = "";

    public PotionCounterHudElement() {
        super(60);
        this.x = 5;
        this.y = 180;
        this.width = 70;
        this.height = 16;
    }

    @Override
    protected String updateText(LadsGraphics g) {
        int pots = g.getGame().getPotionCount();
        int soups = g.getGame().getSoupCount();
        int display = optCycle("Display", 0); // 0 = Both, 1 = Potions only, 2 = Soups only

        if (editor && pots == 0 && soups == 0) {
            pots = 6;
            soups = 2;
        }

        if (pots == lastPots && soups == lastSoups && display == lastDisplay) {
            return cachedText;
        }

        lastPots = pots;
        lastSoups = soups;
        lastDisplay = display;

        return cachedText = switch (display) {
            case 1 -> "Potions: " + pots;
            case 2 -> "Soups: " + soups;
            default -> "Pots: " + pots + " · Soup: " + soups;
        };
    }
}
