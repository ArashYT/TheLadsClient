package com.thelads.core.client.hypixel;

import com.thelads.core.client.bridge.LadsGameBridge;
import java.util.Locale;

/**
 * LowBlockWarning: Alerts the player when placeable blocks in their hotbar drop below a threshold
 * during fast-paced bridging minigames (Bed Wars / The Bridge).
 * Plays a vanilla sound once when dropping below the threshold, and resets when blocks go back up.
 */
public final class LowBlockWarning {
    public static final String LOW_BLOCK = "Low Block Warning";
    public static final String NAME = LOW_BLOCK;
    private static boolean triggered = false;

    private LowBlockWarning() {}

    public static boolean isEnabled() {
        return HypixelMods.isActiveOnCurrentServer() && HypixelMods.on(LOW_BLOCK);
    }

    public static boolean isTriggered() {
        return triggered;
    }

    /**
     * Checks hotbar block count on player tick.
     * Active in Bed Wars and The Bridge only.
     */
    public static void checkHotbar(LadsGameBridge bridge, String gametype, String mode, int threshold) {
        if (bridge == null) return;
        if (!isEnabled()) {
            triggered = false;
            return;
        }

        // Only active in Bed Wars or The Bridge
        boolean isBedWarsOrBridge = false;
        if (gametype != null) {
            String gt = gametype.toUpperCase(Locale.ROOT);
            if (gt.contains("BEDWARS") || gt.contains("BED_WARS")) isBedWarsOrBridge = true;
            if (gt.contains("DUELS") && mode != null && mode.toUpperCase(Locale.ROOT).contains("BRIDGE")) isBedWarsOrBridge = true;
        }
        if (!isBedWarsOrBridge) {
            triggered = false;
            return;
        }

        int count = bridge.getHotbarBlockCount();
        if (count < 0) return;

        if (count <= threshold && count > 0) {
            if (!triggered) {
                triggered = true;
                bridge.playComboSound(0.5f, 1.0f);
                bridge.displayChatMessage("§c[!] Low blocks warning: §e" + count + " §cblocks remaining!");
            }
        } else if (count > threshold) {
            // Reset when block count goes back above threshold
            triggered = false;
        }
    }

    public static void reset() {
        triggered = false;
    }
}
