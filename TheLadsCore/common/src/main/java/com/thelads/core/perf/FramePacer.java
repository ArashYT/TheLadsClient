package com.thelads.core.perf;

import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * Built-in input-latency pacing, the Lads remake of Reflex AntiLag (always on, no setting; each game version feeds it its own GPU
 * fences). In a GPU-bound scene the driver queues finished CPU frames: the game reads input for frame N+1 while the GPU still
 * draws N-1 and N, so a click or a turn shows up two or three GPU frames later. Every frame ends with a fence; before the next
 * frame reads input, {@link #frameStart} waits until the previous frame's fence is about to signal, so the new frame's CPU work is
 * ready just as the GPU frees up: the queue stays near empty and the input is as fresh as it can be, without the GPU going idle.
 *
 * <p>The wake-up is predicted from the moment the frame before last finished (the anchor), the GPU's frame time, the CPU's frame
 * time and a margin. The margin is a feedback loop: when a frame ends and the previous frame's GPU work is already done, the GPU
 * may have run dry, so the margin grows quickly; otherwise it shrinks slowly. CPU-bound or frame-capped frames find the previous
 * fence already signalled and never wait.
 *
 * <p>Render thread only. Times are System.nanoTime values; tests drive it with a simulated clock and GPU.
 */
public final class FramePacer {
    /** Waits until a deadline or until stop answers true; returns the time then. */
    public interface Waiter {
        long until(long deadline, BooleanSupplier stop);
    }

    /** No frame is ever held longer than this. */
    static final long MAX_WAIT = 40_000_000L;
    private final LongSupplier clock;
    private final Waiter waiter;
    /** When the frame read its input (the end of the wait). */
    private long startedAt = -1;
    /** When the frame-end check saw the previous frame's GPU work done; -1 when it was still running. */
    private long previousDoneAt = -1;
    private long lastAnchor = -1;
    /** Averages: GPU frame time (between anchors, GPU-bound only), CPU frame time (input read to present). 0 = unknown. */
    private long gpu, cpu;
    private long margin = -1;
    private boolean paced;
    /** QA counters. */
    public long frames, pacedFrames, waitedNs, dryFrames;

    public FramePacer() {
        this(System::nanoTime, PreciseWait::until);
    }

    public FramePacer(LongSupplier clock, Waiter waiter) {
        this.clock = clock;
        this.waiter = waiter;
    }

    /**
     * Before the frame reads its input. previousDone polls (without blocking) the fence of the last presented frame, olderDone the
     * one before it. Returns the nanoseconds waited; the caller polls input again when it waited.
     */
    public long frameStart(BooleanSupplier previousDone, BooleanSupplier olderDone) {
        long entry = clock.getAsLong();
        frames++;
        if (previousDone.getAsBoolean()) { // the GPU already caught up: CPU-bound or capped, nothing queued to skip
            paced = false;
            lastAnchor = previousDoneAt = -1;
            startedAt = entry;
            return 0;
        }
        // The frame before last finishing is the anchor; while more than one frame is queued this first drains it to one.
        long anchor = previousDoneAt >= 0 ? previousDoneAt : waiter.until(entry + MAX_WAIT, olderDone);
        previousDoneAt = -1;
        if (lastAnchor >= 0 && anchor - lastAnchor > 0 && anchor - lastAnchor < MAX_WAIT) gpu = average(gpu, anchor - lastAnchor);
        lastAnchor = anchor;
        paced = true;
        pacedFrames++;
        long now = clock.getAsLong();
        if (gpu > 0) {
            if (margin < 0) margin = gpu / 4;
            long wake = Math.min(anchor + gpu - cpu - margin, entry + MAX_WAIT);
            if (wake > now) now = waiter.until(wake, previousDone);
        }
        startedAt = now;
        waitedNs += now - entry;
        return now - entry;
    }

    /** Right before the frame is presented, before its own fence is made: previousDone = the last frame's fence signalled. */
    public void frameEnd(boolean previousDone) {
        long now = clock.getAsLong();
        if (startedAt >= 0) cpu = average(cpu, now - startedAt);
        if (paced && gpu > 0) {
            if (previousDone) dryFrames++;
            margin = previousDone ? Math.min(gpu, margin + gpu / 16) : Math.max(0, margin - gpu / 256);
        }
        previousDoneAt = previousDone ? now : -1;
    }

    public long gpuFrameNs() { return gpu; }
    public long cpuFrameNs() { return cpu; }
    public long marginNs() { return margin; }

    private static long average(long average, long sample) {
        return average == 0 ? sample : average + (sample - average) / 8;
    }
}
