package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;

public class PingHudElement extends HudElement {
    public PingHudElement() {
        this.x = 5;
        this.y = 65;
        this.width = 65;
        this.height = 16;
    }

    @Override
    public void render(LadsGraphics g) {
        drawBackground(g);
        int ping = g.getGame().getPing();
        boolean label = optBool("Show label", true);
        boolean colorByPing = optBool("Color by ping", true);

        int c = resolveColor();
        if (colorByPing) {
            if (ping < 60) c = 0xFF55FF55;
            else if (ping < 120) c = 0xFFFFFF55;
            else c = 0xFFFF5555;
        }

        String text = (label ? "Ping: " : "") + ping + "ms";
        this.width = Math.max(55, g.textWidth(text) + 12);
        drawCenteredText(g, text);
    }
}
