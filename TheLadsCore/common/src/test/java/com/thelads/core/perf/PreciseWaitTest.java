package com.thelads.core.perf;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PreciseWaitTest {
    /** A fence that signals early ends the wait about then, not at the far deadline (one long park would hold the frame). */
    @Test
    void aStopConditionEndsTheWaitSoon() {
        long start = System.nanoTime(), signal = start + 3_000_000L;
        long end = PreciseWait.until(start + 40_000_000L, () -> System.nanoTime() >= signal);
        assertTrue(end - signal < 6_000_000L, "returned " + (end - signal) / 1000 + " us after the signal");
    }

    @Test
    void reachesTheDeadline() {
        long start = System.nanoTime(), end = PreciseWait.until(start + 5_000_000L);
        assertTrue(end >= start + 5_000_000L && end < start + 15_000_000L, "took " + (end - start) / 1000 + " us");
    }
}
