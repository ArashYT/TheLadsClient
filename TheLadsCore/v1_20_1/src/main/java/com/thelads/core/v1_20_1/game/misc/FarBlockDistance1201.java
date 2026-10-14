package com.thelads.core.v1_20_1.game.misc;

import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;

/** The Far Block Entities distance, the same rule as 26.3: never below what the renderer asks for, 64 to 256 blocks. */
public final class FarBlockDistance1201 {
    public static final String NAME = "FarBlockEntities";
    /** QA: the last distance a renderer was allowed (read by the QA probe). */
    public static volatile double lastResolved;

    private static volatile Module cachedModule;
    private static volatile SliderOption cachedSlider;

    private FarBlockDistance1201() {}

    public static double resolve(double vanilla) {
        Module module = cachedModule;
        if (module == null) {
            module = ModuleManager.getInstance().getModule(NAME);
            if (module != null) {
                cachedModule = module;
                if (module.getOption("Distance") instanceof SliderOption slider) {
                    cachedSlider = slider;
                }
            }
        }
        SliderOption slider = cachedSlider;
        double requested = slider != null ? slider.getValue() : 128;
        double result = resolve(vanilla, requested, module != null && module.isEnabled());
        lastResolved = result;
        return result;
    }

    public static double resolve(double vanilla, double requested, boolean enabled) {
        if (!enabled || !Double.isFinite(requested)) return vanilla;
        return Math.max(vanilla, Math.min(256, Math.max(64, requested)));
    }
}
