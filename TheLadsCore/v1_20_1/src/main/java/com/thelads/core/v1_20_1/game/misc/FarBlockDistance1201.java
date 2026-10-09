package com.thelads.core.v1_20_1.game.misc;

import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;

/** The Far Block Entities distance, the same rule as 26.3: never below what the renderer asks for, 64 to 256 blocks. */
public final class FarBlockDistance1201 {
    public static final String NAME = "FarBlockEntities";
    /** QA: the last distance a renderer was allowed (read by the QA probe). */
    public static volatile double lastResolved;

    private FarBlockDistance1201() {}

    public static double resolve(double vanilla) {
        Module module = ModuleManager.getInstance().getModule(NAME);
        double requested = module != null && module.getOption("Distance") instanceof SliderOption slider ? slider.getValue() : 128;
        double result = resolve(vanilla, requested, module != null && module.isEnabled());
        lastResolved = result;
        return result;
    }

    public static double resolve(double vanilla, double requested, boolean enabled) {
        if (!enabled || !Double.isFinite(requested)) return vanilla;
        return Math.max(vanilla, Math.min(256, Math.max(64, requested)));
    }
}
