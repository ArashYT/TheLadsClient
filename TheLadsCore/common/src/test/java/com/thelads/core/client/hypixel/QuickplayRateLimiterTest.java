package com.thelads.core.client.hypixel;

import static org.junit.jupiter.api.Assertions.*;

import com.thelads.core.client.hypixel.quickplay.QuickplayRateLimiter;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QuickplayRateLimiterTest {
    private QuickplayRateLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = QuickplayRateLimiter.getInstance();
        limiter.resetForTest();
    }

    @Test
    void testFirstExecutionSucceeds() {
        AtomicBoolean executed = new AtomicBoolean(false);
        AtomicBoolean blocked = new AtomicBoolean(false);

        boolean result = limiter.tryExecute(1000L, () -> executed.set(true), () -> blocked.set(true));
        assertTrue(result);
        assertTrue(executed.get());
        assertFalse(blocked.get());
        assertEquals(3000L, limiter.getRemainingCooldownMs(1000L));
    }

    @Test
    void testExecutionWithinCooldownIsBlocked() {
        limiter.tryExecute(1000L, () -> {}, () -> {});

        AtomicBoolean executed = new AtomicBoolean(false);
        AtomicBoolean blocked = new AtomicBoolean(false);

        // 1500 ms elapsed: < 3000 ms cooldown
        boolean result = limiter.tryExecute(2500L, () -> executed.set(true), () -> blocked.set(true));
        assertFalse(result);
        assertFalse(executed.get());
        assertTrue(blocked.get());
        assertEquals(1500L, limiter.getRemainingCooldownMs(2500L));
    }

    @Test
    void testExecutionJustBeforeCooldownIsBlocked() {
        limiter.tryExecute(1000L, () -> {}, () -> {});

        AtomicBoolean executed = new AtomicBoolean(false);
        AtomicBoolean blocked = new AtomicBoolean(false);

        // 2999 ms elapsed: < 3000 ms cooldown
        boolean result = limiter.tryExecute(3999L, () -> executed.set(true), () -> blocked.set(true));
        assertFalse(result);
        assertFalse(executed.get());
        assertTrue(blocked.get());
        assertEquals(1L, limiter.getRemainingCooldownMs(3999L));
    }

    @Test
    void testExecutionAfterCooldownSucceeds() {
        limiter.tryExecute(1000L, () -> {}, () -> {});

        AtomicBoolean executed = new AtomicBoolean(false);
        AtomicBoolean blocked = new AtomicBoolean(false);

        // 3000 ms elapsed: allowed
        boolean result = limiter.tryExecute(4000L, () -> executed.set(true), () -> blocked.set(true));
        assertTrue(result);
        assertTrue(executed.get());
        assertFalse(blocked.get());
        assertEquals(3000L, limiter.getRemainingCooldownMs(4000L));
        assertEquals(0L, limiter.getRemainingCooldownMs(7000L));
    }

    @Test
    void testResetAllowsImmediateExecution() {
        limiter.tryExecute(1000L, () -> {}, () -> {});
        limiter.resetForTest();

        AtomicBoolean executed = new AtomicBoolean(false);
        boolean result = limiter.tryExecute(1500L, () -> executed.set(true), () -> {});
        assertTrue(result);
        assertTrue(executed.get());
    }
}
