package com.thelads.core.perf;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MemoryGuardTest {
    private long now = 1_000_000L;
    private long used = 0;
    private final long max = 1000;
    private int trims;
    private int cleared;

    private MemoryGuard guard() {
        MemoryGuard g = new MemoryGuard(new MemoryGuard.Heap() {
            public long used() { return used; }
            public long max() { return max; }
        }, new MemoryGuard.Clock() {
            public long millis() { return now; }
        });
        g.add("a", () -> trims++);
        g.add("b", () -> { throw new IllegalStateException("broken cache"); });
        g.add("c", () -> trims++);
        g.addLevelBound("level", () -> cleared++);
        return g;
    }

    @Test
    void firstCallOnlyStartsTheClock() {
        MemoryGuard g = guard();
        used = 990;
        assertEquals(0, g.check());
        assertEquals(0, trims);
    }

    @Test
    void trimsOnlyAfterIntervalAndAboveThreshold() {
        MemoryGuard g = guard();
        g.check();
        used = 851;
        now += MemoryGuard.CHECK_INTERVAL_MS - 1;
        assertEquals(0, g.check());
        now += 1;
        assertEquals(2, g.check()); // the broken cache is skipped, the others still run
        assertEquals(2, trims);
    }

    @Test
    void belowOrAtThresholdDoesNothing() {
        MemoryGuard g = guard();
        g.check();
        used = 850;
        now += MemoryGuard.CHECK_INTERVAL_MS;
        assertEquals(0, g.check());
        assertEquals(0, trims);
    }

    @Test
    void rateLimitedToOneTrimPerTwoMinutes() {
        MemoryGuard g = guard();
        g.check();
        used = 900;
        now += MemoryGuard.CHECK_INTERVAL_MS;
        assertEquals(2, g.check());
        for (long t = MemoryGuard.CHECK_INTERVAL_MS; t < MemoryGuard.TRIM_COOLDOWN_MS; t += MemoryGuard.CHECK_INTERVAL_MS) {
            now += MemoryGuard.CHECK_INTERVAL_MS;
            assertEquals(0, g.check());
        }
        now += MemoryGuard.CHECK_INTERVAL_MS;
        assertEquals(2, g.check());
        assertEquals(4, trims);
    }

    @Test
    void levelChangeClearsLevelCachesWithoutGc() {
        MemoryGuard g = guard();
        assertEquals(1, g.clearLevelBound(false));
        assertEquals(1, cleared);
    }
}
