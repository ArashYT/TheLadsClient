package com.thelads.core.v26_2.feature;

/** Tracks cumulative server-reported player kills; initial history and resets never emit kills. */
public final class ServerKillTracker {
    private boolean initialized;
    private int previous;

    public int observe(int total) {
        if (total < 0) return 0;
        int delta = initialized && total > previous ? total - previous : 0;
        initialized = true;
        previous = total;
        return delta;
    }
    public void reset() { initialized = false; previous = 0; }
}
