package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;

public class XaeroMinimapHudElement extends HudElement {
    public XaeroMinimapHudElement() {
        this.x = 5;
        this.y = 5;
        this.width = 70;
        this.height = 70;
    }

    @Override
    public void render(LadsGraphics g) {
        // No world-map provider is connected. Do not draw a fabricated map.
    }

    @Override
    public boolean isAvailable() { return false; }
}
