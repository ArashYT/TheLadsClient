package com.thelads.core.perf;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * QA only: the in-game evidence run for the built-in input remakes, the same on every game version. Each phase settles for 2 s
 * and measures for 6 s (LatencyMeter): pacing on and off with no extra GPU work, then with a GPU load calibrated until the game is
 * GPU-bound (input-to-GPU-done latency above twice the frame time unpaced), twice each in turn; then optional mouse floods (the
 * version's synthetic high-rate mouse events, per event and batched). The caller ticks it every client tick and owns the knobs.
 */
public final class InputLatencyQa {
    public interface Target {
        void paced(boolean on);
        /** Extra GPU work per frame in the version's own units (0 = none); calibration doubles it from the start value. */
        void gpuLoad(int units);
        /** 0 none, 1 synthetic mouse events delivered one by one (vanilla's path), 2 the same events batched. */
        void flood(int mode);
        void log(String line);
    }

    private static final long SETTLE = 2_000_000_000L, MEASURE = 6_000_000_000L, CALIBRATE = 2_500_000_000L;
    private final Target target;
    private final LatencyMeter meter;
    private final FramePacer pacer;
    private final int[] floods;
    private final List<String[]> phases = new ArrayList<>();
    private final List<double[]> results = new ArrayList<>();
    private int phase = -1, quads;
    private long phaseStart;
    private boolean measuring, calibrating = true, done;
    private long pacedFramesAtStart, framesAtStart, cpuAtStart, wallAtStart;
    /** The render thread's CPU time (it ticks this): window event handling shows here even when it runs inside the frame limiter. */
    private final java.lang.management.ThreadMXBean threads = java.lang.management.ManagementFactory.getThreadMXBean();

    /** startLoad: the first GPU load tried; floods: the flood modes this version supports (e.g. {1, 2}, {1} or none). */
    public InputLatencyQa(Target target, LatencyMeter meter, FramePacer pacer, int startLoad, int... floods) {
        this.target = target;
        this.quads = startLoad;
        this.meter = meter;
        this.pacer = pacer;
        this.floods = floods;
    }

    public boolean done() {
        return done;
    }

    /** Every client tick; returns true once finished (and logged the END or FAILED line). */
    public boolean tick() {
        if (done) return true;
        long now = System.nanoTime();
        if (calibrating) return calibrate(now);
        if (phase >= 0 && !measuring && now - phaseStart >= SETTLE) { // settled: measure
            meter.reset();
            pacedFramesAtStart = pacer.pacedFrames;
            framesAtStart = pacer.frames;
            cpuAtStart = threads.getCurrentThreadCpuTime();
            wallAtStart = now;
            measuring = true;
            phaseStart = now;
        } else if (phase < 0 || measuring && now - phaseStart >= MEASURE) { // measured: next phase
            if (measuring) finishPhase();
            if (++phase >= phases.size()) return finish();
            String[] p = phases.get(phase);
            target.paced("paced".equals(p[1]));
            target.gpuLoad("heavy".equals(p[0]) ? quads : 0);
            target.flood(Integer.parseInt(p[2]));
            phaseStart = now;
        }
        return false;
    }

    /** Unpaced, doubling the GPU load until frames queue (latency above twice the frame time). */
    private boolean calibrate(long now) {
        if (phase < 0) {
            phase = 0;
            target.paced(false);
            target.flood(0);
            target.gpuLoad(quads);
            phaseStart = now;
            meter.reset();
            return false;
        }
        if (now - phaseStart < CALIBRATE) return false;
        double fps = meter.fps(), latency = meter.meanLatencyMs();
        target.log(String.format(Locale.ROOT, "Lads input probe: calibrating: GPU load %d: %s", quads, meter.summary()));
        boolean gpuBound = fps > 0 && latency > 2 * 1000 / fps;
        if (!gpuBound && quads < 32768) {
            quads *= 2;
            target.gpuLoad(quads);
            phaseStart = now;
            meter.reset();
            return false;
        }
        calibrating = false;
        phase = -1;
        for (String load : new String[] {"light", "heavy", "heavy"})
            for (String pace : new String[] {"paced", "unpaced"}) phases.add(new String[] {load, pace, "0"});
        for (int flood : floods) phases.add(new String[] {"light", "paced", String.valueOf(flood)});
        if (floods.length > 0) phases.add(new String[] {"light", "paced", "0"});
        target.log("Lads input probe: GPU load " + quads + " (" + (gpuBound ? "GPU-bound" : "not GPU-bound at the cap") + "), "
            + phases.size() + " phases");
        return false;
    }

    private void finishPhase() {
        String[] p = phases.get(phase);
        long paced = pacer.pacedFrames - pacedFramesAtStart, frames = Math.max(1, pacer.frames - framesAtStart);
        double renderCpu = (threads.getCurrentThreadCpuTime() - cpuAtStart) * 100.0 / Math.max(1, System.nanoTime() - wallAtStart);
        results.add(new double[] {meter.fps(), meter.meanLatencyMs(), renderCpu});
        target.log(String.format(Locale.ROOT, "Lads input probe: phase %d %s %s flood %s: %s; render thread CPU %.1f%%; pacer GPU-bound frames %d%%, GPU %.2f ms, CPU %.2f ms, margin %.2f ms",
            phase + 1, p[0], p[1], p[2], meter.summary(), renderCpu, "paced".equals(p[1]) ? paced * 100 / frames : 0,
            pacer.gpuFrameNs() / 1e6, pacer.cpuFrameNs() / 1e6, Math.max(0, pacer.marginNs()) / 1e6));
        measuring = false;
    }

    private boolean finish() {
        done = true;
        target.paced(true);
        target.gpuLoad(0);
        target.flood(0);
        // phases: 0 light paced, 1 light unpaced, 2/4 heavy paced, 3/5 heavy unpaced
        double lightKept = results.get(0)[0] / results.get(1)[0];
        double heavyPacedFps = (results.get(2)[0] + results.get(4)[0]) / 2, heavyUnpacedFps = (results.get(3)[0] + results.get(5)[0]) / 2;
        double heavyPacedLatency = (results.get(2)[1] + results.get(4)[1]) / 2, heavyUnpacedLatency = (results.get(3)[1] + results.get(5)[1]) / 2;
        String verdict = String.format(Locale.ROOT, "light FPS paced/unpaced %.3f; heavy FPS %.1f paced vs %.1f unpaced (%.3f); heavy latency %.2f ms paced vs %.2f ms unpaced",
            lightKept, heavyPacedFps, heavyUnpacedFps, heavyPacedFps / heavyUnpacedFps, heavyPacedLatency, heavyUnpacedLatency);
        boolean ok = lightKept >= 0.95 && heavyPacedFps >= heavyUnpacedFps * 0.95 && heavyPacedLatency <= heavyUnpacedLatency;
        target.log((ok ? "Lads input probe END: " : "Lads input probe FAILED: ") + verdict);
        return true;
    }
}
