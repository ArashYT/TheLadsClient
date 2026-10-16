package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;

/**
 * SurvivalTweaks (consolidates MiscTweaks): survival and world quality-of-life mechanics.
 */
public final class SurvivalTweaksModule extends Module {
    public static final String NAME = "SurvivalTweaks";

    public final BoolOption berryBushProtection;
    public final BoolOption elytraWaterSwim;
    public final BoolOption noEnchantDurabilityLoss;
    public final BoolOption creeperFullDrops;
    public final BoolOption stableBlockDrops;
    public final BoolOption fasterObsidianMining;
    public final BoolOption flammableCobwebs;
    public final BoolOption animalsEatFloorFood;
    public final SliderOption dimensionBrightness;

    private static volatile SurvivalTweaksModule cachedInstance;

    public SurvivalTweaksModule() {
        super(NAME, "Survival and world quality-of-life tweaks: berry bush armor & sneak protection, faster obsidian mining, "
                + "100% creeper block drops, no Soul Speed/Thorns durability loss, elytra water swim, stable block drops, "
                + "flammable cobwebs, and animals eating dropped food.");
        berryBushProtection = addOption(new BoolOption("Berry bush protection", true));
        elytraWaterSwim = addOption(new BoolOption("Elytra water swim", true));
        noEnchantDurabilityLoss = addOption(new BoolOption("No Thorns/Soul Speed damage", true));
        creeperFullDrops = addOption(new BoolOption("100% creeper block drops", true));
        stableBlockDrops = addOption(new BoolOption("Stable block drops", true));
        fasterObsidianMining = addOption(new BoolOption("Faster obsidian mining", true));
        flammableCobwebs = addOption(new BoolOption("Flammable cobwebs", true));
        animalsEatFloorFood = addOption(new BoolOption("Animals eat floor food", true));
        dimensionBrightness = addOption(new SliderOption("Dimension brightness", 100, 0, 200, 5).percent());
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
        return isEnabled() && berryBushProtection.get();
    }

    public boolean isElytraWaterSwimEnabled() {
        return isEnabled() && elytraWaterSwim.get();
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
        return isEnabled() && fasterObsidianMining.get();
    }

    public boolean isFlammableCobwebsEnabled() {
        return isEnabled() && flammableCobwebs.get();
    }

    public boolean isAnimalsEatFloorFoodEnabled() {
        return isEnabled() && animalsEatFloorFood.get();
    }
}
