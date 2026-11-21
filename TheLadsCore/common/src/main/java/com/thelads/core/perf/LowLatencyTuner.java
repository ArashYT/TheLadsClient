package com.thelads.core.perf;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The maths of the "Low Latency" module (the Lads remake of a Reflex-style frame queue limiter), driven by {@link FramePacer}: the
 * pacer measures the GPU's and the CPU's frame time and decides how long input reading can wait before the next frame; this
 * smooths that wait (an average over about 16 frames of the pacer's own averages), adds the user's offset, backs off when the frame
 * rate falls and caps the wait at one frame of 30 fps. Pure: no clock, no GL, so tests feed it synthetic frame series.
 *
 * <p>The wait is the head start the pacer's plan gives the GPU's backlog: planned = gpu - cpu - margin. GPU-bound (planned &gt; 0):
 * lead = planned x scale + offset, smoothed. CPU-bound (planned &lt;= 0): nothing to wait for, 0 whatever the offset.
 * Back-off: on a paced frame, a frame interval more than 4% over the slower of the GPU and CPU frame time means the wait itself
 * costs frames (the GPU idles); scale then falls 5% per frame, down to 0, and recovers 1% per frame when the interval is fine.
 */
public final class LowLatencyTuner {
    /** One frame of 30 fps: no wait is ever longer. */
    public static final long CAP_NS = 33_333_333L;
    static final double TOLERANCE = 1.04, FALL = 0.05, RISE = 0.01, SMOOTH = 1.0 / 16;
    private volatile long offsetNs;
    private double scale = 1, lead;
    private long gpu, cpu, waited;
    private long waitAverage;
    /** Display values (debug overlay), set from the render thread, read from the same thread. */
    public long frames;

    /** The user's Wait Offset in ms (-2..+5): added to the wait. */
    public void offsetMs(double ms) {
        offsetNs = Math.round(ms * 1e6);
    }

    /**
     * How far past the anchor (the moment the GPU backlog finishes) the next frame may start reading input. planned is the
     * pacer's gpu - cpu - margin; the result is between 0 and {@link #CAP_NS}.
     */
    public long lead(long gpuNs, long cpuNs, long plannedNs) {
        gpu = gpuNs;
        cpu = cpuNs;
        double target = plannedNs > 0 ? plannedNs * scale + offsetNs : 0;
        lead += (Math.max(0, Math.min(CAP_NS, target)) - lead) * SMOOTH;
        if (plannedNs <= 0 && lead < 1_000) lead = 0;
        return (long) lead;
    }

    /** A paced frame ended: its frame-to-frame interval and how long its input read waited. */
    public void frame(long intervalNs, long waitedNs) {
        frames++;
        waited = waitedNs;
        waitAverage += (waitedNs - waitAverage) / 16;
        long slowest = Math.max(gpu, cpu);
        if (slowest <= 0 || intervalNs <= 0) return;
        if (intervalNs > slowest * TOLERANCE) scale = Math.max(0, scale - FALL);
        else scale = Math.min(1, scale + RISE);
    }

    public double scale() { return scale; }
    public long gpuNs() { return gpu; }
    public long cpuNs() { return cpu; }
    public long waitNs() { return waited; }

    /** Overlay lines: cpu, gpu, wait and the input age it saves (the wait itself: input is read that much later in the frame). */
    public List<String> debugLines() {
        return Arrays.asList(String.format(Locale.ROOT, "Low Latency cpu %.2f ms gpu %.2f ms", cpu / 1e6, gpu / 1e6),
            String.format(Locale.ROOT, "Low Latency wait %.2f ms (avg %.2f), input ~%.1f ms fresher, scale %.0f%%", waited / 1e6, waitAverage / 1e6,
                waitAverage / 1e6, scale * 100));
    }
}
