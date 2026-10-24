package com.thelads.core.client.hud;

import com.thelads.core.client.CombatTracker;
import com.thelads.core.client.bridge.LadsGraphics;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PvPInfoHudElement extends TextHudElement {
    private String cachedText = "";
    private int lastLanded = -1, lastTaken = -1, lastGapples = -1;
    private float lastAcc = -1f;
    private int lastLayout = -1;

    public PvPInfoHudElement() {
        super(70);
        this.x = 5;
        this.y = 160;
        this.width = 75;
        this.height = 16;
    }

    @Override
    protected String updateText(LadsGraphics g) {
        CombatTracker ct = CombatTracker.get();
        int landed = ct.getHitsLanded();
        int taken = ct.getHitsTaken();
        int gapples = ct.getGapplesUsed();
        float acc = ct.getAccuracy();

        if (editor && landed == 0 && taken == 0) {
            landed = 24;
            taken = 8;
            gapples = 2;
            acc = 75.0f;
        }

        boolean showAcc = optBool("Show accuracy", true);
        boolean showLanded = optBool("Show hits landed", true);
        boolean showTaken = optBool("Show hits taken", true);
        boolean showGapples = optBool("Show gapples", true);
        int layout = optCycle("Layout", 0); // 0 = Multi-line, 1 = Compact

        if (landed == lastLanded && taken == lastTaken && gapples == lastGapples
                && Float.compare(acc, lastAcc) == 0 && layout == lastLayout) {
            return cachedText;
        }

        lastLanded = landed;
        lastTaken = taken;
        lastGapples = gapples;
        lastAcc = acc;
        lastLayout = layout;

        List<String> parts = new ArrayList<>();
        if (showAcc) parts.add(String.format(Locale.ROOT, "Acc: %.0f%%", acc));
        if (showLanded) parts.add("Hits: " + landed);
        if (showTaken) parts.add("Taken: " + taken);
        if (showGapples) parts.add("Gapples: " + gapples);

        if (parts.isEmpty()) parts.add("PvP Info");

        if (layout == 1) {
            return cachedText = String.join(" · ", parts);
        } else {
            return cachedText = String.join("\n", parts);
        }
    }
}
