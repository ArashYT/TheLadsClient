package com.thelads.core.v1_21_11.feature;

import java.util.function.BooleanSupplier;

/**
 * BadOptimizations (bundled) skips lightmap updates until one of vanilla's inputs changes, and Lads Fullbright changes the
 * gamma inside LightTexture instead of the option. Registered in fabric.mod.json ("badoptimizations:cache_hooks"), so a
 * Fullbright toggle or gamma change marks its lightmap cache dirty. Never loaded without BadOptimizations.
 */
public final class FullbrightLightmapHook implements BooleanSupplier {
    private double last = Double.NaN;

    @Override
    public boolean getAsBoolean() {
        double gamma = NativeFeatures.fullbrightGamma();
        if (Double.compare(gamma, last) == 0) return false;
        last = gamma;
        return true;
    }
}
