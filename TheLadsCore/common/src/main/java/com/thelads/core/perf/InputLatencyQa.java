package com.thelads.core.perf;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * QA only: the in-game evidence run for the built-in input remakes, the same on every game version. Each phase settles for 2 s
 * and measures for 6 s (LatencyMeter): pacing on and off with no extra GPU work, then with a GPU load calibrated until the game is
 * GPU-bound well below the QA frame cap (unpaced input-to-GPU-done latency above twice the frame time, under 80 FPS), twice each in turn; then optional mouse floods (the
 * version's synthetic high-rate mouse events, per event and batched); then the window resized, made fullscreen (F11's path) and
 * restored with the pacing on. LADS_VERIFY_INPUT_SOAK=seconds makes it a light stability run instead: no measurements and no GPU
 * load, the window steps, then that long a run logging every minute. The verdict (END or FAILED line) comes last. The caller ticks it every client tick and owns the knobs.
 */
public final class InputLatencyQa {
    public interface Target {
        void paced(boolean on);
        /** Extra GPU work per frame in the version's own units (0 = none); calibration doubles it from the start value. */
        void gpuLoad(int units);
        /** 0 none, 1 synthetic mouse events delivered one by one (vanilla's path), 2 the same events batched. */
        void flood(int mode);
        /** "resize", "restore", "fullscreen" (as F11 does) or "windowed". */
        void window(String action);
        void log(String line);
    }

    private static final long SETTLE = 2_000_000_000L, MEASURE = 6_000_000_000L, CALIBRATE = 2_500_000_000L, SECOND = 1_000_000_000L;
    private static final String[] WINDOW = {"resize", "restore", "fullscreen", "windowed"};
    private final Target target;
    private final LatencyMeter meter;
    private final FramePacer pacer;
    private final int[] floods;
    private final long soak;
    private final List<String[]> phases = new ArrayList<>();
    private final List<double[]> results = new ArrayList<>();
    private int phase = -1, load, windowStep = -1;
    private long phaseStart, soakStart, nextSoakLog;
    private boolean measuring, calibrating = true, done;
    private String verdict;
    private boolean ok;
    private long pacedFramesAtStart, framesAtStart, cpuAtStart, wallAtStart;
    /** The render thread's CPU time (it ticks this): window event handling shows here even when it runs inside the frame limiter. */
    private final ThreadMXBean threads = ManagementFactory.getThreadMXBean();
    /** Tests drive the phases with their own clock. */
    java.util.function.LongSupplier clock = System::nanoTime;

    /** startLoad: the first GPU load tried; floods: the flood modes this version supports (e.g. {1, 2}, {1} or none). */
    public InputLatencyQa(Target target, LatencyMeter meter, FramePacer pacer, int startLoad, int... floods) {
        this.target = target;
        this.load = startLoad;
        this.meter = meter;
        this.pacer = pacer;
        this.floods = floods;
        long seconds = 0;
        try { seconds = Long.parseLong(String.valueOf(System.getenv("LADS_VERIFY_INPUT_SOAK")).trim()); } catch (NumberFormatException ignored) {}
        this.soak = Math.max(0, seconds) * SECOND;
        if (soak > 0) { // stability run: straight to the window steps and the soak
            calibrating = false;
            verdict = "stability run with the pacing on, no measurements";
            ok = true;
        }
    }

    public boolean done() {
        return done;
    }

    /** True during the long run, where a tick a second is enough. */
    public boolean soaking() {
        return soakStart > 0;
    }

    /** Every client tick; returns true once finished (and logged the END or FAILED line). */
    public boolean tick() {
        if (done) return true;
        long now = clock.getAsLong();
        if (calibrating) return calibrate(now);
        if (verdict != null) {
            if (phaseStart == 0) phaseStart = now;
            return afterMeasurements(now);
        }
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
            if (++phase >= phases.size()) {
                judge();
                phaseStart = now;
                return false;
            }
            String[] p = phases.get(phase);
            target.paced("paced".equals(p[1]));
            target.gpuLoad("heavy".equals(p[0]) ? load : 0);
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
            target.gpuLoad(load);
            phaseStart = now;
            meter.reset();
            return false;
        }
        if (now - phaseStart < CALIBRATE) return false;
        double fps = meter.fps(), latency = meter.meanLatencyMs();
        target.log(String.format(Locale.ROOT, "Lads input probe: calibrating: GPU load %d: %s", load, meter.summary()));
        boolean gpuBound = fps > 0 && latency > 2 * 1000 / fps;
        if ((!gpuBound || fps > 80) && load < 32768) {
            load *= 2;
            target.gpuLoad(load);
            phaseStart = now;
            meter.reset();
            return false;
        }
        calibrating = false;
        phase = -1;
        for (String level : new String[] {"light", "heavy", "heavy"})
            for (String pace : new String[] {"paced", "unpaced"}) phases.add(new String[] {level, pace, "0"});
        for (int flood : floods) phases.add(new String[] {"light", "paced", String.valueOf(flood)});
        if (floods.length > 0) phases.add(new String[] {"light", "paced", "0"});
        target.log("Lads input probe: GPU load " + load + " (" + (gpuBound ? "GPU-bound" : "not GPU-bound at the cap") + "), "
            + phases.size() + " phases");
        return false;
    }

    private void finishPhase() {
        String[] p = phases.get(phase);
        long paced = pacer.pacedFrames - pacedFramesAtStart, frames = Math.max(1, pacer.frames - framesAtStart);
        double renderCpu = (threads.getCurrentThreadCpuTime() - cpuAtStart) * 100.0 / Math.max(1, clock.getAsLong() - wallAtStart);
        results.add(new double[] {meter.fps(), meter.meanLatencyMs(), renderCpu});
        target.log(String.format(Locale.ROOT, "Lads input probe: phase %d %s %s flood %s: %s; render thread CPU %.1f%%; pacer GPU-bound frames %d%%, GPU %.2f ms, CPU %.2f ms, margin %.2f ms",
            phase + 1, p[0], p[1], p[2], meter.summary(), renderCpu, "paced".equals(p[1]) ? paced * 100 / frames : 0,
            pacer.gpuFrameNs() / 1e6, pacer.cpuFrameNs() / 1e6, Math.max(0, pacer.marginNs()) / 1e6));
        measuring = false;
    }

    private void judge() {
        target.paced(true);
        target.gpuLoad(0);
        target.flood(0);
        // phases: 0 light paced, 1 light unpaced, 2/4 heavy paced, 3/5 heavy unpaced
        double lightKept = results.get(0)[0] / results.get(1)[0];
        double heavyPacedFps = (results.get(2)[0] + results.get(4)[0]) / 2, heavyUnpacedFps = (results.get(3)[0] + results.get(5)[0]) / 2;
        double heavyPacedLatency = (results.get(2)[1] + results.get(4)[1]) / 2, heavyUnpacedLatency = (results.get(3)[1] + results.get(5)[1]) / 2;
        verdict = String.format(Locale.ROOT, "light FPS paced/unpaced %.3f; heavy FPS %.1f paced vs %.1f unpaced (%.3f); heavy latency %.2f ms paced vs %.2f ms unpaced",
            lightKept, heavyPacedFps, heavyUnpacedFps, heavyPacedFps / heavyUnpacedFps, heavyPacedLatency, heavyUnpacedLatency);
        ok = lightKept >= 0.95 && heavyPacedFps >= heavyUnpacedFps * 0.95 && heavyPacedLatency <= heavyUnpacedLatency;
        meter.reset();
    }

    /** Window steps 4 s apart, then the soak, then the verdict line. */
    private boolean afterMeasurements(long now) {
        if (windowStep < WINDOW.length) {
            if (now - phaseStart < 4 * SECOND) return false;
            if (windowStep >= 0) target.log("Lads input probe: window " + WINDOW[windowStep] + ": " + meter.summary() + "; " + stats());
            if (++windowStep < WINDOW.length) {
                meter.reset();
                target.window(WINDOW[windowStep]);
                phaseStart = now;
                return false;
            }
            soakStart = soak > 0 ? now : 0;
            nextSoakLog = now + 60 * SECOND;
            meter.reset();
        }
        if (soakStart > 0 && now - soakStart < soak) {
            if (now >= nextSoakLog) {
                target.log(String.format(Locale.ROOT, "Lads input soak: %d s: %s; %s", (now - soakStart) / SECOND, meter.summary(), stats()));
                meter.reset();
                nextSoakLog += 60 * SECOND;
            }
            return false;
        }
        if (soakStart > 0) target.log(String.format(Locale.ROOT, "Lads input soak: %d s done: %s; %s", soak / SECOND, meter.summary(), stats()));
        done = true;
        target.log((ok ? "Lads input probe END: " : "Lads input probe FAILED: ") + verdict + (soak > 0 ? "; soak " + soak / SECOND + " s" : ""));
        return true;
    }

    private String stats() {
        Runtime runtime = Runtime.getRuntime();
        return String.format(Locale.ROOT, "pacer %d/%d frames GPU-bound, waited %d ms, heap %d MB", pacer.pacedFrames, pacer.frames,
            pacer.waitedNs / 1_000_000, (runtime.totalMemory() - runtime.freeMemory()) >> 20);
    }
}
