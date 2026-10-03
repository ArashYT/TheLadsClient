package com.thelads.core.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * QA (each version's zoom capture): the world FOV of every rendered frame while QA drives Lads Zoom through the game's own key
 * and scroll handlers. From a phase's first frame on, each frame must lie on Smooth Zoom's exact curve for the time it was
 * rendered (frame-rate independent), move only toward the goal (no jitter or overshoot) and end exactly on it.
 * {@link #csv()} is the per-frame log.
 */
public final class ZoomTrace {
    private static final double TOLERANCE = 1e-3; // relative; float FOVs carry ~1e-7
    private final StringBuilder csv = new StringBuilder("phase,ms,frame_ms,fov,expected_fov\n");
    private final List<String> failures = new ArrayList<>();
    private final StringBuilder summary = new StringBuilder();
    private String phase;
    private double from, goal, previous, worstDeviation, maxFrameMs;
    private long start, last;
    private int frames, phaseFrames;

    /** Input given: the FOV eases from {@code beforeFov} (the last frame before it) to {@code goalFov}. */
    public void phase(String name, double beforeFov, double goalFov) {
        end();
        phase = name;
        previous = beforeFov;
        goal = goalFov;
        phaseFrames = 0;
    }

    /** A rendered frame: its world FOV and the System.nanoTime() Lads Zoom computed it at (the same twice: no new frame). */
    public void frame(double fov, long nanos) {
        if (phase == null || nanos == last) return;
        if (phaseFrames == 0) { from = fov; start = nanos; }
        double expected = ZoomModule.approach(from, goal, (nanos - start) / 1e9);
        double frameMs = last == 0 ? 0 : (nanos - last) / 1e6;
        csv.append(String.format(Locale.ROOT, "%s,%.3f,%.3f,%.5f,%.5f\n", phase, (nanos - start) / 1e6, frameMs, fov, expected));
        double deviation = Math.abs(fov / expected - 1);
        worstDeviation = Math.max(worstDeviation, deviation);
        if (deviation > TOLERANCE)
            fail(String.format(Locale.ROOT, "%s at %.1f ms: FOV %.4f is off Smooth Zoom's curve (%.4f)", phase, (nanos - start) / 1e6, fov, expected));
        double lo = Math.min(previous, goal), hi = Math.max(previous, goal);
        if (fov < lo * (1 - TOLERANCE) || fov > hi * (1 + TOLERANCE))
            fail(String.format(Locale.ROOT, "%s at %.1f ms: FOV %.4f moved away from %.4f or past it (previous %.4f)", phase, (nanos - start) / 1e6, fov, goal, previous));
        previous = fov;
        last = nanos;
        maxFrameMs = Math.max(maxFrameMs, frameMs);
        frames++;
        phaseFrames++;
    }

    /** Closes the trace: every phase must have ended exactly on its goal. */
    public void finish() {
        end();
        phase = null;
    }

    private void end() {
        if (phase == null) return;
        if (phaseFrames < 2) fail(phase + ": only " + phaseFrames + " frames rendered");
        else if (Math.abs(previous / goal - 1) > 1e-5) fail(String.format(Locale.ROOT, "%s: ended at FOV %.5f, not %.5f", phase, previous, goal));
        summary.append(String.format(Locale.ROOT, "%s %.2f->%.2f in %d frames; ", phase, from, goal, phaseFrames));
    }

    private void fail(String failure) {
        if (failures.size() < 20) failures.add(failure);
    }

    public List<String> failures() { return failures; }
    public String csv() { return csv.toString(); }

    public String summary() {
        return String.format(Locale.ROOT, "%s%d frames, slowest %.1f ms, worst deviation from the curve %.2e", summary, frames, maxFrameMs, worstDeviation);
    }
}
