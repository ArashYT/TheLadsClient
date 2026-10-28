package com.thelads.core.modules;

import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;

/**
 * SurvivalTweaks (consolidates MiscTweaks): survival and world quality-of-life mechanics.
 */
public final class SurvivalTweaksModule extends Module {
    public static final String NAME = "SurvivalTweaks";
    /**
     * The server-rules name of the options that change what a server checks: the dig speed (Faster obsidian mining) and the
     * player's movement (Berry bush protection's slowdown, Elytra water swim). A server without them sees ghost-fast mining and moves
     * its simulation does not expect, so they act only in singleplayer, LAN and trusted servers (HypixelSafetyManager).
     */
    public static final String MECHANICS = "SurvivalMechanics";

    public final BoolOption berryBushProtection;
    public final BoolOption elytraWaterSwim;
    public final BoolOption noEnchantDurabilityLoss;
    public final BoolOption creeperFullDrops;
    public final BoolOption stableBlockDrops;
    public final BoolOption fasterObsidianMining;
    public final BoolOption flammableCobwebs;
    public final BoolOption animalsEatFloorFood;
    public final SliderOption dimensionBrightness;
    public final BoolOption boat360View;
    public final BoolOption longerChatHistory;
    public final BoolOption fastIpPing;
    public final BoolOption keepBarsWhileRiding;

    private static volatile SurvivalTweaksModule cachedInstance;
    private final java.util.Set<String> notHere = new java.util.HashSet<>();

    /** An adapter's game version has no such thing (1.8.9: no berry bushes, elytra or boat look limit): the menu leaves it out. */
    public void hideHere(String... options) {
        notHere.addAll(java.util.List.of(options));
    }

    public boolean hidden(com.thelads.core.config.Option option) {
        return notHere.contains(option.getName());
    }

    public SurvivalTweaksModule() {
        super(NAME, "Survival and world quality-of-life tweaks: berry bush armor & sneak protection, faster obsidian mining, "
                + "100% creeper block drops, no Soul Speed/Thorns durability loss, elytra water swim, stable block drops, "
                + "flammable cobwebs, animals eating dropped food, boat 360 view, longer chat history, fast IP ping, and keeping status bars while riding. "
                + "Faster obsidian mining, berry bush protection and elytra water swim only work in singleplayer, LAN and servers known to allow them.");
        berryBushProtection = addOption(new BoolOption("Berry bush protection", true));
        elytraWaterSwim = addOption(new BoolOption("Elytra water swim", true));
        noEnchantDurabilityLoss = addOption(new BoolOption("No Thorns/Soul Speed damage", true));
        creeperFullDrops = addOption(new BoolOption("100% creeper block drops", true));
        stableBlockDrops = addOption(new BoolOption("Stable block drops", true));
        fasterObsidianMining = addOption(new BoolOption("Faster obsidian mining", true));
        flammableCobwebs = addOption(new BoolOption("Flammable cobwebs", true));
        animalsEatFloorFood = addOption(new BoolOption("Animals eat floor food", true));
        dimensionBrightness = addOption(new SliderOption("Dimension brightness", 100, 0, 200, 5).percent());
        boat360View = addOption(new BoolOption("Boat 360 View", true));
        longerChatHistory = addOption(new BoolOption("Longer Chat History", true));
        fastIpPing = addOption(new BoolOption("Fast IP Ping", true));
        keepBarsWhileRiding = addOption(new BoolOption("Keep Bars While Riding", true));
        setEnabled(true);
    }

    public static SurvivalTweaksModule get() {
        SurvivalTweaksModule instance = cachedInstance;
        if (instance == null) {
            if (ModuleManager.getInstance().getModule(NAME) instanceof SurvivalTweaksModule m) {
                cachedInstance = instance = m;
            }
        }
        return instance;
    }

    public boolean isBerryBushProtectionEnabled() {
        return isEnabled() && berryBushProtection.get() && !HypixelSafetyManager.isDisallowed(MECHANICS);
    }

    public boolean isElytraWaterSwimEnabled() {
        return isEnabled() && elytraWaterSwim.get() && !HypixelSafetyManager.isDisallowed(MECHANICS);
    }

    public boolean isNoEnchantDurabilityLossEnabled() {
        return isEnabled() && noEnchantDurabilityLoss.get();
    }

    public boolean isCreeperFullDropsEnabled() {
        return isEnabled() && creeperFullDrops.get();
    }

    public boolean isStableBlockDropsEnabled() {
        return isEnabled() && stableBlockDrops.get();
    }

    public boolean isFasterObsidianMiningEnabled() {
        return isEnabled() && fasterObsidianMining.get() && !HypixelSafetyManager.isDisallowed(MECHANICS);
    }

    /**
     * The lightmap's gamma scaled by Dimension brightness. Past vanilla's Bright (1.0) it is a Fullbright, so brighter than that only
     * where Fullbright's server rules allow it.
     */
    public float brightness(float gamma) {
        int pct = dimensionBrightness.getIntValue();
        if (pct == 100) return gamma;
        float scaled = gamma * (pct / 100.0f);
        return pct > 100 && scaled > 1.0f && HypixelSafetyManager.isDisallowed("Fullbright") ? Math.max(gamma, 1.0f) : scaled;
    }

    public boolean isFlammableCobwebsEnabled() {
        return isEnabled() && flammableCobwebs.get();
    }

    public boolean isAnimalsEatFloorFoodEnabled() {
        return isEnabled() && animalsEatFloorFood.get();
    }

    public boolean isBoat360ViewEnabled() {
        return isEnabled() && boat360View.get();
    }

    public boolean isLongerChatHistoryEnabled() {
        return isEnabled() && longerChatHistory.get();
    }

    public boolean isFastIpPingEnabled() {
        return isEnabled() && fastIpPing.get();
    }

    public boolean isKeepBarsWhileRidingEnabled() {
        return isEnabled() && keepBarsWhileRiding.get();
    }
}
