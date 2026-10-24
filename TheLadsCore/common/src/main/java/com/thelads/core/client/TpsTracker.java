package com.thelads.core.client;

public final class TpsTracker {
    private static final TpsTracker INSTANCE = new TpsTracker();
    public static TpsTracker get() { return INSTANCE; }

    private static final int MAX_SAMPLES = 20;
    private final long[] timeSamples = new long[MAX_SAMPLES];
    private int head = 0;
    private int count = 0;
    private double currentTps = 20.0;

    private TpsTracker() {}

    public synchronized void onTimeUpdate(long worldTime) {
        long now = System.currentTimeMillis();
        if (count > 0) {
            int prevIdx = (head - 1 + MAX_SAMPLES) % MAX_SAMPLES;
            long delta = now - timeSamples[prevIdx];
            if (delta > 50L && delta < 15000L) {
                // Vanilla sends 20 ticks of time update roughly every 1000ms.
                double sampleTps = Math.min(20.0, (20.0 * 1000.0) / delta);
                currentTps = currentTps * 0.75 + sampleTps * 0.25;
            }
        }
        timeSamples[head] = now;
        head = (head + 1) % MAX_SAMPLES;
        if (count < MAX_SAMPLES) count++;
    }

    public synchronized double getTps() {
        return Math.max(0.0, Math.min(20.0, currentTps));
    }

    public synchronized void reset() {
        count = 0;
        head = 0;
        currentTps = 20.0;
    }
}
