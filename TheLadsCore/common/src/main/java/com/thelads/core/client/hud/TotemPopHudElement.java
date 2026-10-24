package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.config.HudSettings;

import java.util.HashMap;
import java.util.Map;

/**
 * Lunar & Feather parity Totem Pop Counter HUD element.
 * Tracks and displays opponent totem of undying pop counts.
 * GATED: Combat advantage.
 */
public class TotemPopHudElement extends HudElement {
    public static final String MODULE_NAME = "TotemPopCounter";

    private static final Map<String, Integer> popCounts = new HashMap<>();
    private static String lastPopPlayer = "";
    private static long lastPopTime = 0;

    public static synchronized void recordPop(String playerName) {
        if (playerName == null || playerName.isEmpty()) return;
        int next = popCounts.getOrDefault(playerName, 0) + 1;
        popCounts.put(playerName, next);
        lastPopPlayer = playerName;
        lastPopTime = System.currentTimeMillis();
    }

    public static synchronized void resetPlayer(String playerName) {
        if (playerName != null) popCounts.remove(playerName);
    }

    public static synchronized void resetAll() {
        popCounts.clear();
        lastPopPlayer = "";
        lastPopTime = 0;
    }

    public TotemPopHudElement() {
        useOrganizedDefaults();
    }

    @Override
    public void prepareRender(LadsGraphics graphics, boolean editor) {
        width = 110;
        height = 18;
    }

    @Override
    public void render(LadsGraphics graphics) {
        if (graphics == null) return;
        drawBackground(graphics);

        String display;
        if (!lastPopPlayer.isEmpty() && (System.currentTimeMillis() - lastPopTime < 6000)) {
            display = lastPopPlayer + ": " + popCounts.getOrDefault(lastPopPlayer, 1) + " pops";
        } else {
            // Preview / idle
            display = "Totems: Ready";
        }

        boolean shadow = HudSettings.getInstance().isTextShadow();
        int textCol = resolveColor();
        int tw = graphics.textWidth(display);
        if (tw + 8 > width) width = tw + 8;
        graphics.drawCenteredText(display, x + width / 2, y + 5, textCol, shadow);
    }
}
