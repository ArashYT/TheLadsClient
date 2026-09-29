package com.thelads.core.v26_2.feature;

/** Exponential animation measured in seconds, independent of game tick and frame rate. */
public final class FrameAnimation {
    private long previousTime;
    private double value;
    private boolean initialized;

    public double update(double target, double speed, long time, boolean enabled) {
        long elapsed = time - previousTime;
        if (!enabled || !initialized || elapsed < 0 || elapsed > 500_000_000L) {
            value = target;
            initialized = true;
        } else {
            double seconds = Math.max(0, elapsed) / 1_000_000_000d;
            value += (target - value) * -Math.expm1(-Math.max(0, speed) * seconds);
            if (Math.abs(target - value) < .001) value = target;
        }
        previousTime = time;
        return value;
    }
}
