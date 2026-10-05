package com.thelads.core.modules;

import java.util.Arrays;

/**
 * Async's self-check for one dimension: parallel ticking stays on only while it is measurably faster than normal ticking.
 *
 * Every RECHECK parallel ticks it ticks one WINDOW normally and compares the median entity-loop time per entity with the
 * parallel ticks just before. Two comparisons in a row where parallel is not at least 5% faster switch it off ("benched").
 * Benched, it tries one parallel WINDOW after a back-off (5 minutes, doubling up to 40) and comes back only if that is at least
 * 15% faster. The gap between the two margins, the second comparison and the back-off keep it from flapping.
 */
public final class AsyncSelfCheck {
    public enum Change { NONE, BENCHED, RESUMED }

    static final int WINDOW = 20, WARMUP = 2, FIRST_CHECK = 100, CONFIRM = 40, RECHECK = 600, FIRST_BACKOFF = 6000, MAX_BACKOFF = 48000;
    static final double SLOWER = 0.95, FASTER = 0.85;

    /** Last ticks of the steady mode (a ring) and the ticks of the other mode under test, as nanoseconds per entity. */
    private final double[] steady = new double[WINDOW - WARMUP], trial = new double[WINDOW - WARMUP];
    private int steadyCount, steadyNext, trialTicks, countdown = FIRST_CHECK, strikes, backoff = FIRST_BACKOFF;
    private boolean benched, testing;
    private double lastParallel, lastNormal;

    /** How the next tick runs: in parallel, or normally. */
    public boolean parallel() {
        return testing ? benched : !benched;
    }

    /** After each tick that ran as parallel() said: the entity loop's time and how many entities it ticked. */
    public Change record(long nanos, int entities) {
        double perEntity = nanos / (double) Math.max(1, entities);
        if (!testing) {
            steady[steadyNext] = perEntity;
            steadyNext = (steadyNext + 1) % steady.length;
            steadyCount++;
            if (--countdown <= 0 && steadyCount >= steady.length) { testing = true; trialTicks = 0; }
            return Change.NONE;
        }
        if (++trialTicks > WARMUP) trial[trialTicks - WARMUP - 1] = perEntity; // the first ticks after a switch run cold
        if (trialTicks < WINDOW) return Change.NONE;
        testing = false;
        double tried = median(trial), usual = median(steady);
        steadyCount = 0;
        lastParallel = benched ? tried : usual;
        lastNormal = benched ? usual : tried;
        if (!benched) {
            if (lastParallel <= lastNormal * SLOWER) { strikes = 0; countdown = RECHECK; return Change.NONE; }
            if (++strikes < 2) { countdown = CONFIRM; return Change.NONE; }
            strikes = 0;
            benched = true;
            countdown = backoff;
            return Change.BENCHED;
        }
        if (lastParallel < lastNormal * FASTER) {
            benched = false;
            backoff = FIRST_BACKOFF;
            countdown = RECHECK;
            return Change.RESUMED;
        }
        backoff = Math.min(backoff * 2, MAX_BACKOFF);
        countdown = backoff;
        return Change.NONE;
    }

    public boolean benched() { return benched; }

    /** Medians of the last comparison, nanoseconds per entity. */
    public double lastParallel() { return lastParallel; }
    public double lastNormal() { return lastNormal; }

    /** Normal ticks before the next parallel try while benched. */
    public int backoffTicks() { return backoff; }

    private static double median(double[] values) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        int n = sorted.length;
        return n % 2 == 1 ? sorted[n / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2;
    }
}
