package com.thelads.core.perf;

import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

/**
 * Waits for a System.nanoTime deadline on the render thread without pumping window events: parks while the deadline is far away,
 * then spins the last stretch. A park on Windows overshoots by about a millisecond (more on a busy PC), so the spin covers twice
 * the recent average overshoot. Used by the built-in input pacing (FramePacer) and by the 1.20.1 frame limiter.
 */
public final class PreciseWait {
    private static final long MIN_SPIN = 300_000L, MAX_SPIN = 3_000_000L;
    /** Recent average park overshoot; render thread only. */
    private static long overshoot = 1_000_000L;

    private PreciseWait() {}

    /** Returns System.nanoTime() once the deadline passed or stop answered true (checked between short waits). */
    public static long until(long deadline, BooleanSupplier stop) {
        long now = System.nanoTime();
        while (now < deadline && !stop.getAsBoolean()) {
            long left = deadline - now, spin = Math.max(MIN_SPIN, Math.min(MAX_SPIN, overshoot * 2));
            if (left > spin) {
                long asked = left - spin;
                LockSupport.parkNanos(asked);
                long after = System.nanoTime();
                overshoot += (Math.max(0, after - now - asked) - overshoot) / 8;
                now = after;
            } else {
                Thread.onSpinWait();
                now = System.nanoTime();
            }
        }
        return now;
    }
}
