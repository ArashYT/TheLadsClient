package com.thelads.ladsapi.test;

import com.thelads.ladsapi.util.RateLimiter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RateLimitTest {

    @Test
    public void testRateLimiter8PerSecond() {
        RateLimiter limiter = new RateLimiter(8);
        long now = 100000L;

        // First 8 calls within the second must succeed
        for (int i = 0; i < 8; i++) {
            assertTrue(limiter.tryAcquire(now + (i * 50)), "Message " + (i + 1) + " should be allowed");
        }

        // 9th and 10th calls in the same second must fail
        assertFalse(limiter.tryAcquire(now + 500), "9th message in same second should be rate limited");
        assertFalse(limiter.tryAcquire(now + 900), "10th message in same second should be rate limited");

        // After window shifts past 1000ms, calls should succeed again
        assertTrue(limiter.tryAcquire(now + 1051), "Message after 1s window should succeed");
    }
}
