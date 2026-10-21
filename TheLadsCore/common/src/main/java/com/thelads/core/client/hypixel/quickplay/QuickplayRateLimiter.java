package com.thelads.core.client.hypixel.quickplay;

/**
 * Thread-safe rate limiter for Quickplay commands.
 * Hypixel kicks or flags players for command spam; enforces at most 1 command per 3,000 ms.
 */
public final class QuickplayRateLimiter {
    public static final long COOLDOWN_MS = 3000L;
    private static final QuickplayRateLimiter INSTANCE = new QuickplayRateLimiter();

    private long lastExecutionTimeMs = 0L;

    public static QuickplayRateLimiter getInstance() {
        return INSTANCE;
    }

    public synchronized boolean tryExecute(Runnable sendAction, Runnable onBlocked) {
        return tryExecute(System.currentTimeMillis(), sendAction, onBlocked);
    }

    public synchronized boolean tryExecute(long currentTimeMs, Runnable sendAction, Runnable onBlocked) {
        long elapsed = currentTimeMs - lastExecutionTimeMs;
        if (elapsed < COOLDOWN_MS && lastExecutionTimeMs != 0L) {
            if (onBlocked != null) onBlocked.run();
            return false;
        }
        lastExecutionTimeMs = currentTimeMs;
        if (sendAction != null) sendAction.run();
        return true;
    }

    public synchronized long getRemainingCooldownMs() {
        return getRemainingCooldownMs(System.currentTimeMillis());
    }

    public synchronized long getRemainingCooldownMs(long currentTimeMs) {
        long elapsed = currentTimeMs - lastExecutionTimeMs;
        if (elapsed >= COOLDOWN_MS || lastExecutionTimeMs == 0L) return 0L;
        return COOLDOWN_MS - elapsed;
    }

    public synchronized void resetForTest() {
        lastExecutionTimeMs = 0L;
    }
}
