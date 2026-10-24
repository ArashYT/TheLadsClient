package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.bridge.LadsGraphics;

/**
 * Small HUD widget: renders the player's avatar skin face/head icon.
 * Visual-only.
 */
public class PlayerHeadHudElement extends HudElement {
    public static final String MODULE_NAME = "PlayerHead";

    public PlayerHeadHudElement() {
        useOrganizedDefaults();
    }

    @Override
    public void prepareRender(LadsGraphics graphics, boolean editor) {
        width = 24;
        height = 24;
    }

    @Override
    public void render(LadsGraphics graphics) {
        if (graphics == null) return;
        drawBackground(graphics);

        LadsGameBridge bridge = LadsGameBridge.get();
        String name = bridge != null ? bridge.getPlayerName() : "Player";
        if (name == null || name.isEmpty()) name = "Player";

        graphics.drawHead(name, null, x + 4, y + 4, 16);
    }
}
