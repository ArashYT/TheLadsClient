package com.thelads.core.client.hud;

import com.thelads.core.client.CombatTracker;
import com.thelads.core.client.bridge.LadsGraphics;

public class ComboCounterHudElement extends TextHudElement {
    private int lastCombo = -1;
    private int lastMode = -1;
    private boolean lastHideZero = false;
    private String cachedText = "";

    public ComboCounterHudElement() {
        super(50);
        this.x = 5;
        this.y = 140;
        this.width = 60;
        this.height = 16;
    }

    @Override
    protected String updateText(LadsGraphics g) {
        int combo = CombatTracker.get().getCombo();
        int mode = optCycle("Display", 0); // 0 = Combo: X, 1 = X Hits, 2 = Number only
        boolean hideZero = optBool("Hide when zero", false);

        if (editor && combo == 0) {
            combo = 3;
        }

        if (hideZero && combo == 0) {
            return "";
        }

        if (combo == lastCombo && mode == lastMode && hideZero == lastHideZero) {
            return cachedText;
        }

        lastCombo = combo;
        lastMode = mode;
        lastHideZero = hideZero;

        return cachedText = switch (mode) {
            case 1 -> combo + " Hits";
            case 2 -> String.valueOf(combo);
            default -> "Combo: " + combo;
        };
    }
}
