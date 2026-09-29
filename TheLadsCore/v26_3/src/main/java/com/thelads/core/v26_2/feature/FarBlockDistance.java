package com.thelads.core.v26_2.feature;

public final class FarBlockDistance {
    private FarBlockDistance() {}
    public static double resolve(double vanilla, double requested, boolean enabled) {
        if (!enabled || !Double.isFinite(requested)) return vanilla;
        return Math.max(vanilla, Math.clamp(requested, 64, 256));
    }
}
