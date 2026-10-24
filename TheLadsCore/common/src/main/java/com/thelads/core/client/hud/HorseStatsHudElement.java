package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.config.HudSettings;

/**
 * Lunar & Feather parity Horse Stats HUD element.
 * Displays jump height, speed, and health of horses.
 * GATED: Attribute info advantage.
 */
public class HorseStatsHudElement extends HudElement {
    public static final String MODULE_NAME = "HorseStats";

    public HorseStatsHudElement() {
        useOrganizedDefaults();
    }

    @Override
    public void prepareRender(LadsGraphics graphics, boolean editor) {
        width = 110;
        height = 34;
    }

    @Override
    public void render(LadsGraphics graphics) {
        if (graphics == null) return;
        LadsGameBridge bridge = LadsGameBridge.get();
        LadsGameBridge.HorseStatsInfo stats = bridge != null ? bridge.getRidingHorseStats() : null;

        float jump, speed, hp, maxHp;
        if (stats != null) {
            jump = stats.jumpHeightBlocks();
            speed = stats.speedMps();
            hp = stats.health();
            maxHp = stats.maxHealth();
        } else {
            // Editor / preview default
            jump = 3.6f;
            speed = 11.4f;
            hp = 26.0f;
            maxHp = 26.0f;
        }

        drawBackground(graphics);

        boolean shadow = HudSettings.getInstance().isTextShadow();
        int textCol = resolveColor();
        graphics.drawText("Horse Stats", x + 4, y + 3, textCol, shadow);

        String hpText = String.format(java.util.Locale.ROOT, "HP: %.0f/%.0f", hp, maxHp);
        graphics.drawText(hpText, x + 4, y + 13, 0xFFFF5555, shadow);

        String statsLine = String.format(java.util.Locale.ROOT, "J: %.1fb  S: %.1fm/s", jump, speed);
        graphics.drawText(statsLine, x + 4, y + 23, 0xFFFFAA00, shadow);
    }
}
