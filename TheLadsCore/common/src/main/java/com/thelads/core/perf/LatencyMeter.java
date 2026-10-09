package com.thelads.core.perf;

import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * QA only: how long each frame's input waits until the GPU finished that frame (input read to GPU done), with its own GPU fence
 * per frame, polled (never waited on) at the frame's hook points: right before the frame reads input, before its swap and after
 * it. A fence is seen done at the first poll after it signalled, so a sample is at most one poll interval late. Also the frame
 * times (input read to input read). Render thread only.
 */
public final class LatencyMeter {
    private static final int IN_FLIGHT = 8, MAX_SAMPLES = 1 << 16;
    private final Supplier<Object> newFence;
    private final Predicate<Object> done;
    private final Consumer<Object> delete;
    private final Object[] fences = new Object[IN_FLIGHT];
    private final long[] inputAt = new long[IN_FLIGHT];
    private long lastInput = -1;
    private final long[] latency = new long[MAX_SAMPLES], frame = new long[MAX_SAMPLES];
    private int latencies, frames;

    public LatencyMeter(Supplier<Object> newFence, Predicate<Object> done, Consumer<Object> delete) {
        this.newFence = newFence;
        this.done = done;
        this.delete = delete;
    }

    /** The frame read its input now (after any pacing wait). */
    public void input() {
        poll();
        long now = System.nanoTime();
        if (lastInput >= 0 && frames < MAX_SAMPLES) frame[frames++] = now - lastInput;
        lastInput = now;
    }

    /** Right before the swap: this frame's fence, tagged with when it read its input. */
    public void fence() {
        poll();
        if (lastInput < 0) return;
        for (int i = 0; i < IN_FLIGHT; i++) {
            if (fences[i] != null) continue;
            fences[i] = newFence.get();
            inputAt[i] = lastInput;
            return;
        }
    }

    public void poll() {
        long now = -1;
        for (int i = 0; i < IN_FLIGHT; i++) {
            if (fences[i] == null || !done.test(fences[i])) continue;
            if (now < 0) now = System.nanoTime();
            if (latencies < MAX_SAMPLES) latency[latencies++] = now - inputAt[i];
            delete.accept(fences[i]);
            fences[i] = null;
        }
    }

    /** Starts a new measurement window (fences in flight stay and report into it). */
    public void reset() {
        latencies = frames = 0;
    }

    /** "fps F, frame p50/p99 X/Y ms, latency mean/p50/p95 A/B/C ms (n samples)". */
    public String summary() {
        long[] f = Arrays.copyOf(frame, frames), l = Arrays.copyOf(latency, latencies);
        Arrays.sort(f);
        Arrays.sort(l);
        double total = 0;
        for (long v : f) total += v;
        double mean = 0;
        for (long v : l) mean += v;
        return String.format(java.util.Locale.ROOT, "fps %.1f, frame p50/p99 %.2f/%.2f ms, latency mean/p50/p95 %.2f/%.2f/%.2f ms (%d frames)",
            f.length == 0 ? 0 : f.length * 1e9 / total, ms(f, 0.5), ms(f, 0.99), l.length == 0 ? 0 : mean / l.length / 1e6, ms(l, 0.5), ms(l, 0.95), l.length);
    }

    public double meanLatencyMs() {
        double mean = 0;
        for (int i = 0; i < latencies; i++) mean += latency[i];
        return latencies == 0 ? 0 : mean / latencies / 1e6;
    }

    public double fps() {
        double total = 0;
        for (int i = 0; i < frames; i++) total += frame[i];
        return frames == 0 ? 0 : frames * 1e9 / total;
    }

    private static double ms(long[] sorted, double quantile) {
        return sorted.length == 0 ? 0 : sorted[Math.min(sorted.length - 1, (int) (sorted.length * quantile))] / 1e6;
    }
}
