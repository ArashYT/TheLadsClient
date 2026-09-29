package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;

public class PingHudElement extends TextHudElement {
    private int preparedPing;
    public PingHudElement() {
        super(55);
        this.x = 5;
        this.y = 65;
        this.width = 65;
        this.height = 16;
    }

    @Override
    protected String updateText(LadsGraphics g) {
        int ping = g.getGame().getPing();
        preparedPing = ping;
        boolean label = optBool("Show label", true);
        return (label ? "Ping: " : "") + ping + "ms";
    }

    @Override protected int textColor() {
        boolean colorByPing = optBool("Color by ping", true);

        int c = resolveColor();
        if (colorByPing) {
            if (preparedPing < 60) c = 0xFF55FF55;
            else if (preparedPing < 120) c = 0xFFFFFF55;
            else c = 0xFFFF5555;
        }

        return c;
    }
}
