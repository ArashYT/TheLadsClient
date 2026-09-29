package com.thelads.core.v26_2.feature.food;

import com.thelads.core.v26_2.feature.NativeQualityOfLife;

/** Live Lads options replace the upstream config file and separate settings screen. */
public final class FoodOverlayConfig {
    public static final FoodOverlayConfig INSTANCE = new FoodOverlayConfig();
    public boolean showFoodValuesInTooltip, showFoodValuesInTooltipAlways;
    public boolean showSaturationHudOverlay, showGainedSaturationHudOverlay;
    public boolean showFoodValuesHudOverlay, showFoodValuesHudOverlayWhenOffhand;
    public boolean showFoodExhaustionHudUnderlay, showFoodHealthHudOverlay, showVanillaAnimationsOverlay;
    public float maxHudOverlayFlashAlpha;

    public static boolean enabled() {
        return NativeFoodOverlay.active() && NativeQualityOfLife.enabled("AppleSkin");
    }

    public static void refresh() {
        INSTANCE.showFoodValuesInTooltip = option("Food Tooltips");
        INSTANCE.showFoodValuesInTooltipAlways = INSTANCE.showFoodValuesInTooltip && option("Tooltips Always Visible");
        INSTANCE.showSaturationHudOverlay = option("Show Saturation");
        INSTANCE.showGainedSaturationHudOverlay = option("Show Saturation Overlay");
        INSTANCE.showFoodValuesHudOverlay = option("Show Food Values");
        INSTANCE.showFoodValuesHudOverlayWhenOffhand = option("Offhand Food");
        INSTANCE.showFoodExhaustionHudUnderlay = option("Show Exhaustion");
        INSTANCE.showFoodHealthHudOverlay = option("Show Health Overlay");
        INSTANCE.showVanillaAnimationsOverlay = option("Vanilla Animations");
        INSTANCE.maxHudOverlayFlashAlpha = (float) NativeQualityOfLife.number("AppleSkin", "Overlay Opacity", 65) / 100;
    }

    private static boolean option(String name) { return NativeQualityOfLife.bool("AppleSkin", name, true); }
}
