package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.hypixel.RealPing;

public class PingHudElement extends TextHudElement {
    private int preparedPing;
    public PingHudElement() {
        super(55);
        this.x = 5;
        this.y = 65;
        this.width = 65;
        this.height = 16;
    }

    private int lastPing = Integer.MIN_VALUE;
    private boolean lastLabel;
    private String cachedText = "";

    @Override
    protected String updateText(LadsGraphics g) {
        // 1.9.1: where the tab list's ping is fake (Hypixel's 1 ms) this is the measured one, "-" until there is one.
        int ping = RealPing.own(g.getGame().getPing());
        preparedPing = ping;
        boolean label = optBool("Show label", true);
        if (ping == lastPing && label == lastLabel) {
            return cachedText;
        }
        lastPing = ping;
        lastLabel = label;
        return cachedText = (label ? "Ping: " : "") + (ping == RealPing.NONE ? "-" : ping + "ms");
    }

    @Override protected int textColor() {
        boolean colorByPing = optBool("Color by ping", true);

        int c = resolveColor();
        if (colorByPing && preparedPing != RealPing.NONE) {
            if (preparedPing < 60) c = 0xFF55FF55;
            else if (preparedPing < 120) c = 0xFFFFFF55;
            else c = 0xFFFF5555;
        }

        return c;
    }
}
