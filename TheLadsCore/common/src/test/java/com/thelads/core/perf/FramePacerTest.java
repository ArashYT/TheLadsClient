package com.thelads.core.perf;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;
import java.util.function.IntToLongFunction;
import org.junit.jupiter.api.Test;

/** FramePacer against a simulated CPU, driver queue (3 frames, as drivers allow by default) and GPU. */
class FramePacerTest {
    private static final long MS = 1_000_000L;
    private static final int WARMUP = 200, FRAMES = 2000;

    /** One run: average input-to-GPU-done latency (ms) and GPU frames per second. */
    private record Result(double latencyMs, double fps, long waitedNs) {}

    private static final class Sim {
        long t;
        Result run(boolean pace, IntToLongFunction cpu, IntToLongFunction gpu) {
            int queue = 3, offset = queue;
            long[] end = new long[WARMUP + FRAMES + offset];
            FramePacer pacer = new FramePacer(() -> t, (deadline, stop) -> {
                while (t < deadline && !stop.getAsBoolean()) t += 10_000;
                return t;
            });
            double latency = 0;
            long firstEnd = 0, waitedAtWarmup = 0;
            for (int k = 0; k < WARMUP + FRAMES; k++) {
                int i = k + offset;
                if (k == WARMUP) waitedAtWarmup = pacer.waitedNs;
                if (pace) pacer.frameStart(() -> t >= end[i - 1], () -> t >= end[i - 2]);
                long start = t;
                t += cpu.applyAsLong(k);
                if (pace) pacer.frameEnd(t >= end[i - 1]);
                t = Math.max(t, end[i - queue]); // present blocks while the driver queue is full
                end[i] = Math.max(t, end[i - 1]) + gpu.applyAsLong(k);
                if (k == WARMUP - 1) firstEnd = end[i];
                if (k >= WARMUP) latency += end[i] - start;
            }
            long last = end[WARMUP + FRAMES + offset - 1];
            return new Result(latency / FRAMES / MS, FRAMES * 1e9 / (last - firstEnd), pacer.waitedNs - waitedAtWarmup);
        }
    }

    private static Result run(boolean pace, IntToLongFunction cpu, IntToLongFunction gpu) {
        Result result = new Sim().run(pace, cpu, gpu);
        System.out.println((pace ? "paced   " : "unpaced ") + result); // the REPORT quotes these
        return result;
    }

    @Test
    void gpuBoundCutsLatencyWithoutLosingFrames() {
        Result off = run(false, k -> 4 * MS, k -> 10 * MS), on = run(true, k -> 4 * MS, k -> 10 * MS);
        assertTrue(on.latencyMs() < off.latencyMs() * 0.6, "latency " + on + " vs " + off);
        assertTrue(on.fps() >= off.fps() * 0.98, "frame rate " + on + " vs " + off);
    }

    @Test
    void jitteryGpuBoundSceneStillGains() {
        Random cpuRandom = new Random(1), gpuRandom = new Random(2);
        long[] cpu = new long[WARMUP + FRAMES], gpu = new long[WARMUP + FRAMES];
        for (int k = 0; k < cpu.length; k++) {
            cpu[k] = 2 * MS + (long) (cpuRandom.nextDouble() * 5 * MS);
            gpu[k] = 8 * MS + (long) (gpuRandom.nextDouble() * 5 * MS);
        }
        Result off = run(false, k -> cpu[k], k -> gpu[k]), on = run(true, k -> cpu[k], k -> gpu[k]);
        assertTrue(on.latencyMs() < off.latencyMs() * 0.7, "latency " + on + " vs " + off);
        assertTrue(on.fps() >= off.fps() * 0.96, "frame rate " + on + " vs " + off);
    }

    @Test
    void cpuBoundNeverWaits() {
        Result off = run(false, k -> 8 * MS, k -> 3 * MS), on = run(true, k -> 8 * MS, k -> 3 * MS);
        assertEquals(0, on.waitedNs(), "a CPU-bound game finds the GPU idle at every frame start");
        assertEquals(off.fps(), on.fps(), 0.01);
    }

    @Test
    void sceneTurningCpuBoundStopsWaiting() {
        IntToLongFunction cpu = k -> k < WARMUP + FRAMES / 2 ? 4 * MS : 9 * MS, gpu = k -> k < WARMUP + FRAMES / 2 ? 10 * MS : 3 * MS;
        Result off = run(false, cpu, gpu), on = run(true, cpu, gpu);
        assertTrue(on.fps() >= off.fps() * 0.98, "frame rate " + on + " vs " + off);
        assertTrue(on.latencyMs() < off.latencyMs(), "latency " + on + " vs " + off);
    }

    @Test
    void neverHoldsAFrameLongerThanTheCap() {
        long[] t = {0};
        FramePacer pacer = new FramePacer(() -> t[0], (deadline, stop) -> {
            while (t[0] < deadline && !stop.getAsBoolean()) t[0] += 10_000;
            return t[0];
        });
        for (int k = 0; k < 50; k++) { // a GPU that never finishes (hung driver)
            long waited = pacer.frameStart(() -> false, () -> false);
            assertTrue(waited <= 2 * FramePacer.MAX_WAIT, "waited " + waited);
            t[0] += MS;
            pacer.frameEnd(false);
        }
    }
}
