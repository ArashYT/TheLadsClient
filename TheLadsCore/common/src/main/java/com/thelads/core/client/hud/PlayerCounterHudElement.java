package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.config.StringOption;

/**
 * Player Counter (Badlion): players on the server (the tab list) and other players loaded near you (HudExtras.players*, written by each
 * version's tick). Gated: the nearby count is a mini radar.
 */
public class PlayerCounterHudElement extends TextHudElement {
    private String text = "", prefix;
    private int total = -1, nearby = -1, display = -1;

    public PlayerCounterHudElement() {
        super(60);
    }

    @Override
    protected String updateText(LadsGraphics g) {
        int t = editor && HudExtras.playersTotal == 0 ? 24 : HudExtras.playersTotal, n = editor && HudExtras.playersTotal == 0 ? 3 : HudExtras.playersNearby;
        int d = optCycle("Display", 0);
        String p = option("Prefix") instanceof StringOption s ? s.get() : "Players: ";
        if (t != total || n != nearby || d != display || !p.equals(prefix)) {
            text = p + switch (d) {
                case 1 -> Integer.toString(n);
                case 2 -> Integer.toString(t);
                default -> t + " (" + n + " near)";
            };
            total = t;
            nearby = n;
            display = d;
            prefix = p;
        }
        return text;
    }
}
