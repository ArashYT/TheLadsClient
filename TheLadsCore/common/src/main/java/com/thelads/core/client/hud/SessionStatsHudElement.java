package com.thelads.core.client.hud;

import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.hypixel.SessionTracker;

/**
 * Session Stats HUD Element. Renders active session statistics while connected to Hypixel.
 */
public class SessionStatsHudElement extends TextHudElement {
    public SessionStatsHudElement() {
        super(100);
        this.x = 10;
        this.y = 120;
        this.width = 110;
        this.height = 40;
    }

    @Override
    public boolean isAvailable() {
        if (editor) return true;
        return HypixelSafetyManager.isHypixel();
    }

    @Override
    protected String updateText(LadsGraphics graphics) {
        if (editor) {
            return "§eSession Stats§r\nGames: 0 (0W - 0L)\nStreak: 0 (Best: 0)\nK/D: 0.00 (0/0)";
        }
        if (!HypixelSafetyManager.isHypixel()) return "";
        return SessionTracker.formatHudText();
    }
}
