package snownee.jade.network;

import org.junit.jupiter.api.Test;

public final class RequestLimiterTest {
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Check failed: " + message);
        }
    }

    @Test
    public void testBurstAndRefill() {
        runChecks();
    }

    public static void runChecks() {
        long t0 = 0L;
        RequestLimiter.Bucket bucket = new RequestLimiter.Bucket(t0);

        // Burst of 40 requests should all be allowed
        for (int i = 0; i < 40; i++) {
            check(bucket.take(t0), "Request " + (i + 1) + " within initial burst of 40 should be allowed");
        }

        // 41st request with no time elapsed must be dropped
        check(!bucket.take(t0), "Request 41 with no time elapsed should be dropped");
        check(!bucket.take(t0), "Request 42 with no time elapsed should be dropped");

        // After 1 second (1,000,000,000 ns), tokens should refill completely (40 tokens)
        long t1 = 1_000_000_000L;
        for (int i = 0; i < 40; i++) {
            check(bucket.take(t1), "Request " + (i + 1) + " after 1s refill should be allowed");
        }

        // Request 41 after refill must drop again
        check(!bucket.take(t1), "Request 41 after 1s refill should be dropped");

        // Partial refill test: after 500ms (20 tokens)
        long t2 = t1 + 500_000_000L;
        for (int i = 0; i < 20; i++) {
            check(bucket.take(t2), "Request " + (i + 1) + " after 500ms partial refill should be allowed");
        }
        check(!bucket.take(t2), "Request 21 after 500ms partial refill should be dropped");
    }

    public static void main(String[] args) {
        runChecks();
        System.out.println("RequestLimiterTest passed successfully.");
    }
}
