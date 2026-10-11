package com.thelads.core.perf;

/**
 * A smooth colour field around the camera (the biome sky and fog colours: 216 biome lookups per sample, three samples a frame)
 * sampled at most once per game tick and interpolated for the frames in between. Within a tick the frames' camera moves in a
 * straight line from A (partial tick 0) to B (partial tick 1), so this keeps the samples at A and B and answers a query on that
 * segment by linear interpolation; the next tick's A is this tick's B, so moving costs one sample per tick. A query off the
 * segment (third person, another mod's position) is left to the caller, which samples as before. Render thread only.
 */
public final class TickSampledColor {
    /** How far (squared, in the caller's units) a query may be off the camera's path: floating-point noise only. */
    private static final double ON_PATH = 1e-8;

    /** Samples the field at a position into rgb. */
    public interface Sampler {
        void sample(double x, double y, double z, double[] rgb);
    }

    private long tick = Long.MIN_VALUE;
    private double ax = Double.NaN, ay, az, bx = Double.NaN, by, bz;
    private final double[] colorA = new double[3], colorB = new double[3];
    /** Samples taken (for the test). */
    int samples;

    /**
     * Sets {@code out} to the colour at p, sampling only when the tick or the camera's path changed; returns false (out untouched,
     * nothing sampled) when p is not on the path from A to B.
     */
    public boolean get(long tick, double ax, double ay, double az, double bx, double by, double bz, double px, double py, double pz,
                       Sampler sampler, double[] out) {
        double dx = bx - ax, dy = by - ay, dz = bz - az, length2 = dx * dx + dy * dy + dz * dz;
        double t = length2 < 1e-12 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy + (pz - az) * dz) / length2));
        double ox = ax + t * dx - px, oy = ay + t * dy - py, oz = az + t * dz - pz;
        if (ox * ox + oy * oy + oz * oz > ON_PATH) return false;
        if (tick != this.tick || ax != this.ax || ay != this.ay || az != this.az || bx != this.bx || by != this.by || bz != this.bz) {
            if (ax == this.bx && ay == this.by && az == this.bz) System.arraycopy(colorB, 0, colorA, 0, 3); // last tick ended here
            else sample(sampler, ax, ay, az, colorA); // first query, or a teleport
            sample(sampler, bx, by, bz, colorB);
            this.tick = tick;
            this.ax = ax;
            this.ay = ay;
            this.az = az;
            this.bx = bx;
            this.by = by;
            this.bz = bz;
        }
        for (int i = 0; i < 3; i++) out[i] = colorA[i] + t * (colorB[i] - colorA[i]);
        return true;
    }

    private void sample(Sampler sampler, double x, double y, double z, double[] rgb) {
        sampler.sample(x, y, z, rgb);
        samples++;
    }
}
