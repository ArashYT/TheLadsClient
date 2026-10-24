package com.thelads.core.client;

import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.LootBeamsModule;

public final class LootBeams {
    public static final String MODULE_NAME = "LootBeams";
    private static LootBeamsModule module;
    private static volatile boolean active;
    private static volatile int beamStyle;
    private static volatile float beamHeight = 2.0f;
    private static volatile boolean colorByRarity = true;
    private static volatile int customColor = 0xFFFFAA00;
    private static volatile float opacity = 0.75f;

    public static final int RARITY_COMMON = 0xFFFFFFFF;
    public static final int RARITY_UNCOMMON = 0xFFFFFF55;
    public static final int RARITY_RARE = 0xFF55FFFF;
    public static final int RARITY_EPIC = 0xFFFF55FF;

    private LootBeams() {}

    public static void setModule(LootBeamsModule m) {
        module = m;
        refresh();
    }

    public static void refresh() {
        LootBeamsModule m = null;
        try {
            var mgr = ModuleManager.getInstance();
            if (mgr != null && mgr.getModule(MODULE_NAME) instanceof LootBeamsModule lm) {
                m = lm;
            }
        } catch (Throwable ignored) {}
        if (m == null) m = module;
        if (m == null) {
            active = false;
            return;
        }
        active = m.isEnabled() && !HypixelSafetyManager.isDisallowed(MODULE_NAME);
        beamStyle = m.beamStyle.getIndex();
        beamHeight = (float) m.beamHeight.getValue();
        colorByRarity = m.colorByRarity.get();
        customColor = m.customColor.getColor();
        opacity = (float) (m.beamOpacity.getValue() / 100.0);
    }

    public static boolean isEnabled() {
        if (active && HypixelSafetyManager.isDisallowed(MODULE_NAME)) return false;
        return active;
    }

    public static int getBeamStyle() { return beamStyle; }
    public static float getBeamHeight() { return beamHeight; }
    public static boolean isColorByRarity() { return colorByRarity; }
    public static int getCustomColor() { return customColor; }
    public static float getOpacity() { return opacity; }

    public static int getColorForRarity(int rarity) {
        if (!colorByRarity) return customColor;
        return switch (rarity) {
            case 1 -> RARITY_UNCOMMON;
            case 2 -> RARITY_RARE;
            case 3 -> RARITY_EPIC;
            default -> RARITY_COMMON;
        };
    }
}
