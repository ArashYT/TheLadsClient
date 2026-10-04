package com.thelads.core.modules;

import java.util.ArrayList;
import java.util.List;

/**
 * QA (each version's sprint probe): every client tick's sprint state while QA walks a toggled sprint into a wall, a hit, hunger,
 * an item, sneaking, water, blindness, flying and a death. "sent" is the sprint state the client last told the server (a change
 * is one START_SPRINTING or STOP_SPRINTING packet). A sent state that flips back within {@link #FLICKER_TICKS} ticks is the
 * start-stop spam this guards against. {@link #csv()} is the per-tick log.
 */
public final class SprintTrace {
    static final int FLICKER_TICKS = 3;
    private final StringBuilder csv = new StringBuilder("tick,phase,sprinting,sent,detail\n");
    private final List<String> failures = new ArrayList<>();
    private final StringBuilder summary = new StringBuilder();
    private String phase;
    private int tick, phaseChanges, lastChange = Integer.MIN_VALUE / 2, changes;
    private Boolean sent;

    public void phase(String name) {
        end();
        phase = name;
        phaseChanges = 0;
        lastChange = Integer.MIN_VALUE / 2; // QA's own change of scene between phases is no flicker
    }

    /** One client tick, after the player's tick sent its state. */
    public void tick(boolean sprinting, boolean sentNow, String detail) {
        if (phase == null) return;
        tick++;
        if (sent != null && sentNow != sent) {
            if (tick - lastChange <= FLICKER_TICKS)
                failures.add(phase + " tick " + tick + ": the sent sprint state flipped back after " + (tick - lastChange) + " ticks");
            lastChange = tick;
            phaseChanges++;
            changes++;
        }
        sent = sentNow;
        csv.append(tick).append(',').append(phase).append(',').append(sprinting).append(',').append(sentNow).append(',').append(detail).append('\n');
    }

    public boolean sent() { return Boolean.TRUE.equals(sent); }
    /** Packets (sent-state changes) in the current phase so far. */
    public int phaseChanges() { return phaseChanges; }

    public void finish() {
        end();
        phase = null;
    }

    private void end() {
        if (phase != null) summary.append(summary.length() == 0 ? "" : "; ").append(phase).append(' ').append(phaseChanges);
    }

    public void fail(String failure) { failures.add(failure); }
    public List<String> failures() { return failures; }
    public String csv() { return csv.toString(); }
    public String summary() { return tick + " ticks, " + changes + " sprint packets (per phase: " + summary + ")"; }
}
