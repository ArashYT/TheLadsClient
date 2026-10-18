package com.thelads.smp.features.arise.nb2;

/**
 * Menu navigation tweaks:
 * right-clicking cycle buttons cycles backward, and mouse scroll wheel adjusts option sliders (YUNG's Menu Tweaks).
 */
public final class YungsMenuTweaksManager {
    private YungsMenuTweaksManager() {}

    public static boolean isRightClickReversesCycleButtonsEnabled() {
        return NB2Config.get().rightClickReversesCycleButtons;
    }

    public static boolean isMouseScrollWheelAffectsSlidersEnabled() {
        return NB2Config.get().mouseScrollWheelAffectsSliders;
    }

    public static double calculateAdjustedSliderValue(double currentValue, double scrollDelta) {
        double step = 0.05;
        double change = scrollDelta > 0 ? step : -step;
        double newValue = currentValue + change;
        return Math.max(0.0, Math.min(1.0, newValue));
    }
}
