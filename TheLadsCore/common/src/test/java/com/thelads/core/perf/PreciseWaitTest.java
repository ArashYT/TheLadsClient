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

    /** While loading the spin is capped at about a millisecond however late the parks wake; in game it keeps up to 3 ms. */
    @Test
    void loadingCapsTheSpin() {
        assertEquals(1_000_000L, PreciseWait.spin(5_000_000L, PreciseWait.LOADING_SPIN));
        assertEquals(3_000_000L, PreciseWait.spin(5_000_000L, Long.MAX_VALUE));
        assertEquals(600_000L, PreciseWait.spin(300_000L, PreciseWait.LOADING_SPIN));
        assertEquals(300_000L, PreciseWait.spin(0L, PreciseWait.LOADING_SPIN));
        long start = System.nanoTime(), end = PreciseWait.until(start + 5_000_000L, PreciseWait.LOADING_SPIN);
        assertTrue(end >= start + 5_000_000L, "returned " + (start + 5_000_000L - end) / 1000 + " us early");
    }
}
