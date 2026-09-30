package com.thelads.core.client;

/** Pure sizing and cadence policy; never changes a window, GUI scale, or frame limiter. */
public final class RenderScalePolicy {
    public record Settings(boolean enabled, int preset, double percent, boolean nearest,
                           boolean dynamic, int target, double minimum) {
        public double maximumScale() {
            return switch (preset) {
                case 1 -> .50;
                case 2 -> .75;
                case 3 -> .85;
                case 4 -> 1.50;
                default -> clamp(percent / 100.0, .50, 2.0);
            };
        }
        public double minimumScale() { return clamp(minimum / 100.0, .50, maximumScale()); }
    }
    public record Size(int width, int height) {}
    private Settings previous;
    private double scale = 1, averageSeconds;
    private long previousFrame, lastAdjustment;

    public double frame(Settings settings, long now, boolean active) {
        double maximum = settings.maximumScale();
        if (!settings.equals(previous) || !active) {
            previous = settings;
            scale = maximum;
            averageSeconds = 0;
            previousFrame = now;
            lastAdjustment = now;
            return scale;
        }
        double seconds = (now - previousFrame) / 1_000_000_000.0;
        previousFrame = now;
        if (!settings.dynamic() || settings.target() <= 0) return scale = maximum;
        // Loading, debugger stops and minimized windows must not drive resolution downward.
        if (seconds <= 0 || seconds > .25) {
            averageSeconds = 0;
            lastAdjustment = now;
            return scale;
        }
        averageSeconds = averageSeconds == 0 ? seconds : averageSeconds + .08 * (seconds - averageSeconds);
        if (now - lastAdjustment < 1_000_000_000L) return scale;
        double budget = 1.0 / settings.target();
        if (averageSeconds > budget * 1.12) scale = Math.max(settings.minimumScale(), scale - .05);
        else if (averageSeconds < budget * .88) scale = Math.min(maximum, scale + .05);
        lastAdjustment = now;
        return scale;
    }

    public static Size size(int width, int height, double scale, int textureLimit) {
        int limit = Math.max(1, textureLimit);
        double bounded = Math.min(clamp(scale, .50, 2.0), Math.min((double) limit / Math.max(1, width), (double) limit / Math.max(1, height)));
        return new Size(Math.max(1, Math.min(limit, (int) Math.round(Math.max(1, width) * bounded))),
            Math.max(1, Math.min(limit, (int) Math.round(Math.max(1, height) * bounded))));
    }
    private static double clamp(double value, double min, double max) {
        return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : max;
    }
}
