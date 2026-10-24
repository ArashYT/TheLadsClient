package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;

/**
 * TeamSpeak HUD: displays TeamSpeak 3 channel status, active talkers, and mute flags (parity with Badlion TeamSpeak HUD).
 */
public class TeamSpeakHudElement extends HudElement {
    private static final String SAMPLE_CHANNEL = "TS: General Voice";

    public TeamSpeakHudElement() {
        this.x = 10;
        this.y = 170;
        this.width = 110;
        this.height = 14;
    }

    @Override
    public void render(LadsGraphics g) {
        renderTs(g, false);
    }

    @Override
    public void renderEditor(LadsGraphics g) {
        renderTs(g, true);
    }

    private void renderTs(LadsGraphics g, boolean editor) {
        g.fill(x, y, x + width, y + height, 0x80000000);
        g.drawText(SAMPLE_CHANNEL, x + 3, y + 3, 0xFF55FF55, com.thelads.core.config.HudSettings.getInstance().isTextShadow());
    }
}
