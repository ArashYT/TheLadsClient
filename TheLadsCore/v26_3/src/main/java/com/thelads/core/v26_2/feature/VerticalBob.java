package com.thelads.core.v26_2.feature;

/** Smooth, bounded camera displacement in blocks, driven by vertical velocity in blocks/tick. */
public final class VerticalBob {
    private long previousTime;
    private double displacement;
    private boolean initialized;

    public float update(double velocity, boolean grounded, double intensity, double accessibility,
                        long now, boolean active) {
        if (!active || !Double.isFinite(velocity) || !Double.isFinite(intensity) || !Double.isFinite(accessibility)) {
            reset();
            return 0;
        }
        long elapsed = now - previousTime;
        previousTime = now;
        if (!initialized || elapsed < 0 || elapsed > 250_000_000L) {
            initialized = true;
            displacement = 0;
            return 0;
        }
        double strength = Math.clamp(intensity, 0, 1.5) * Math.clamp(accessibility, 0, 1);
        double target = grounded ? 0 : Math.clamp(-velocity * .12, -.075, .075) * strength;
        displacement += (target - displacement) * -Math.expm1(-14 * elapsed / 1_000_000_000d);
        if (Math.abs(displacement) < .000001) displacement = 0;
        return (float) displacement;
    }

    public void reset() {
        initialized = false;
        displacement = 0;
    }
}
