package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.bridge.LadsGraphics;

/**
 * Badlion-parity Player Counter HUD element.
 * Displays count of connected multiplayer players and nearby render-distance players.
 * GATED: Nearby count is a mini-radar.
 */
public class PlayerCounterHudElement extends TextHudElement {
    public static final String MODULE_NAME = "PlayerCounter";

    public PlayerCounterHudElement() {
        super(80);
        useOrganizedDefaults();
    }

    @Override
    protected String updateText(LadsGraphics graphics) {
        LadsGameBridge bridge = LadsGameBridge.get();
        int total = bridge != null ? bridge.getConnectedPlayerCount() : 1;
        int nearby = bridge != null ? bridge.getNearbyPlayerCount() : 1;

        if (total <= 1 && editor) {
            total = 24;
            nearby = 4;
        }

        return "Players: " + total + " (" + nearby + ")";
    }
}
