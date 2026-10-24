package com.thelads.core.client.survival;

import com.thelads.core.modules.SurvivalTweaksModule;

/**
 * Survival guard helpers: prevent breaking tools at low durability, accidental log stripping, and mining budding amethyst (parity with Badlion).
 */
public final class SurvivalGuards {
    private SurvivalGuards() {}

    public static boolean shouldBlockTool(int damage, int maxDamage) {
        SurvivalTweaksModule mod = SurvivalTweaksModule.get();
        if (mod == null || !mod.isBlockToolAtLowDurabilityEnabled()) return false;
        if (maxDamage <= 0) return false;
        int remaining = maxDamage - damage;
        return remaining <= mod.getLowDurabilityThreshold();
    }

    public static boolean shouldBlockStripLog(boolean hasAxe, boolean isLog, boolean isSneaking) {
        SurvivalTweaksModule mod = SurvivalTweaksModule.get();
        if (mod == null || !mod.isAntiStripLogEnabled()) return false;
        return hasAxe && isLog && !isSneaking;
    }

    public static boolean shouldBlockBuddingAmethyst(boolean isBuddingAmethyst) {
        SurvivalTweaksModule mod = SurvivalTweaksModule.get();
        if (mod == null || !mod.isAntiBuddingAmethystEnabled()) return false;
        return isBuddingAmethyst;
    }
}
