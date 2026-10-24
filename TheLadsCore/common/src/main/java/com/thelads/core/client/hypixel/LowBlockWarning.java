package com.thelads.core.client.hypixel;

import com.thelads.core.client.bridge.LadsGameBridge;

/**
 * LowBlockWarning: Plays an audio warning cue and alerts the player when their hotbar block supply
 * drops below a set threshold in Hypixel minigames (Bed Wars, SkyWars, etc.).
 */
public final class LowBlockWarning {
    public static final String NAME = "Low Block Warning";
    private static int lastCount = -1;
    private static long lastWarnTime = 0;

    private LowBlockWarning() {}

    public static boolean isEnabled() {
        return HypixelMods.isActiveOnCurrentServer() && HypixelMods.on(NAME);
    }

    /**
     * Checks the current hotbar block count. If count transitioned from > threshold to <= threshold,
     * fires the warning sound and visual alert.
     */
    public static boolean checkBlocks(int currentBlockCount, long nowNanos) {
        if (!isEnabled()) {
            lastCount = currentBlockCount;
            return false;
        }

        int threshold = HypixelMods.lowBlockThreshold();
        boolean shouldWarn = (lastCount > threshold || lastCount == -1) && currentBlockCount <= threshold && currentBlockCount > 0;
        lastCount = currentBlockCount;

        if (shouldWarn && (lastWarnTime == 0 || nowNanos - lastWarnTime >= 2_000_000_000L)) { // 2s cooldown
            lastWarnTime = nowNanos;
            LadsGameBridge bridge = LadsGameBridge.get();
            if (bridge != null) {
                bridge.displayChatMessage("§c§l[Low Blocks] §eOnly " + currentBlockCount + " blocks remaining!");
            }
            return true;
        }
        return false;
    }

    public static void reset() {
        lastCount = -1;
        lastWarnTime = 0;
    }
}
