package com.thelads.core.client.qa;

import com.thelads.core.client.bridge.LadsGraphics;
import java.util.ArrayDeque;
import java.util.function.LongSupplier;

/**
 * QA only (permanent owner rule: every QA run shows a live progress bar): "Lads QA - run - step i/N - current - OK n - SLOW n -
 * FAIL n - CRASH n - ETA m:ss" as a boss-bar-style bar at the top centre, drawn after the Lads HUD on every version
 * ({@link #draw}; the versions' HUD hooks call it). Green while clean, yellow once a step was SLOW, red on a FAIL or CRASH. The ETA
 * is the average finished step time times the steps left. The bar stays up after {@link #done} with the final state.
 * <p>
 * Active only when the Verification harness started the game ({@code -Dthelads.qaBar=1}, or LADS_QA_BAR in the environment): for
 * players every call is a no-op on a constant, nothing is drawn. A QA flow calls {@link #begin}, then {@link #step} once per step
 * (that finishes the one before as OK, or SLOW past {@link #slowAfter}), {@link #result} for a step that did not just pass, and
 * {@link #done}. A run begun while another is running is nested in it: the bar shows the inner one, and when it is done the outer
 * one's open step takes its worst result. A check that compares HUD pixels hides the bar first: {@link #hideFrames} or {@link #hide}.
 */
public final class QaProgress {
    public enum Result { OK, SLOW, FAIL, CRASH }

    private static final class Run {
        final String name;
        int total, index, ok, slow, fail, crash;
        String label = "";
        boolean open, ended;
        long stepStart, finishedMs, finishedCount, slowMs = 120_000;
        Run(String name, int total) { this.name = name; this.total = Math.max(0, total); }
    }

    private static boolean on = flag(System.getProperty("thelads.qaBar", System.getenv("LADS_QA_BAR")));
    private static LongSupplier clock = System::currentTimeMillis;
    private static Run run;
    private static final ArrayDeque<Run> OUTER = new ArrayDeque<>();
    private static int hideFrames;
    private static boolean hidden, finalShotDue;
    private static long endedAt;

    private QaProgress() {}

    private static boolean flag(String value) {
        return value != null && !value.isEmpty() && !value.equals("0") && !value.equalsIgnoreCase("false");
    }

    /** Whether this game runs under the QA harness: the bar exists. */
    public static boolean on() { return on; }

    /** A new run of {@code total} steps (0: not known, the bar is striped instead of filling); nested in the one still running, else it replaces the one shown. */
    public static synchronized void begin(String runName, int totalSteps) {
        if (!on) return;
        if (run != null && !run.ended) OUTER.push(run);
        else OUTER.clear();
        finalShotDue = false;
        run = new Run(runName, totalSteps);
    }

    /** Starts the next step; the one still open passes (SLOW if it took longer than {@link #slowAfter}). More steps than announced raise the total. */
    public static synchronized void step(String stepLabel) {
        if (!on || run == null || run.ended) return;
        finish(run, null);
        run.index++;
        if (run.total > 0 && run.index > run.total) run.total = run.index;
        run.label = stepLabel == null ? "" : stepLabel;
        run.open = true;
        run.stepStart = clock.getAsLong();
    }

    /** A check: the next step, named for it, failed when it did not hold (the one before passes). */
    public static synchronized void check(String what, boolean ok) {
        step(what);
        if (!ok) result(Result.FAIL);
    }

    /** Renames the open step (a step that learns what it is doing as it goes). */
    public static synchronized void label(String stepLabel) {
        if (on && run != null && run.open) run.label = stepLabel == null ? "" : stepLabel;
    }

    /** Ends the open step with this result (a later {@link #step} then does not pass it again); with none open, counts one more of it. */
    public static synchronized void result(Result result) {
        if (!on || run == null) return;
        if (run.open) finish(run, result);
        else count(run, result);
    }

    /** Ends the run: the open step passes; the bar stays with the final counts and fills (a nested run hands its worst result to the run around it). */
    public static synchronized void done() {
        if (!on || run == null || run.ended) return;
        finish(run, null);
        run.ended = true;
        run.label = "done";
        if (OUTER.isEmpty()) {
            finalShotDue = true;
            endedAt = clock.getAsLong();
            return;
        }
        Run inner = run;
        run = OUTER.pop();
        Result worst = inner.crash > 0 ? Result.CRASH : inner.fail > 0 ? Result.FAIL : inner.slow > 0 ? Result.SLOW : null;
        if (worst != null) result(worst);
    }

    /**
     * A quarter of a second after the outermost run is done (the game then has drawn the final state), once: the name of the screenshot ("qa-bar-final-&lt;run&gt;") that shows the bar's final
     * state, which the version's QA driver then saves; null otherwise.
     */
    public static synchronized String finalShot() {
        if (!on || !finalShotDue || run == null || !run.ended || clock.getAsLong() - endedAt < 250) return null;
        finalShotDue = false;
        return "qa-bar-final-" + run.name.replaceAll("[^A-Za-z0-9._-]+", "-");
    }

    /** True from {@link #begin} until {@link #done} of the outermost run. */
    public static synchronized boolean running() { return on && run != null && !run.ended; }

    /** The name of the run now showing, or null. */
    public static synchronized String run() { return run == null ? null : run.name; }

    /** After {@link #begin}: steps that take longer than this many ms count as SLOW (default 2 min). */
    public static synchronized void slowAfter(long ms) { if (run != null) run.slowMs = ms; }

    /** Counts of the finished steps {OK, SLOW, FAIL, CRASH}. */
    public static synchronized int[] counts() { return run == null ? new int[4] : new int[] {run.ok, run.slow, run.fail, run.crash}; }

    /** Leaves the bar out of the next {@code frames} frames drawn (a pixel check on a frame that is about to render). */
    public static synchronized void hideFrames(int frames) { if (on) hideFrames = Math.max(hideFrames, frames); }

    /** Leaves the bar out until {@code hide(false)} (a run whose frames are all compared). */
    public static synchronized void hide(boolean hide) { if (on) hidden = hide; }

    private static void finish(Run r, Result result) {
        if (!r.open) return;
        long took = clock.getAsLong() - r.stepStart;
        count(r, result != null ? result : took > r.slowMs ? Result.SLOW : Result.OK);
        r.finishedMs += took;
        r.finishedCount++;
        r.open = false;
    }

    private static void count(Run r, Result result) {
        switch (result) {
            case OK -> r.ok++;
            case SLOW -> r.slow++;
            case FAIL -> r.fail++;
            case CRASH -> r.crash++;
        }
    }

    /** m:ss for the average finished step time times the steps left; "--:--" before the first step finished or when the total is not known. */
    static synchronized String eta() {
        if (run == null || run.finishedCount == 0 || run.total == 0) return "--:--";
        long left = Math.max(0, run.total - run.finishedCount);
        long seconds = Math.round(left * (double) run.finishedMs / run.finishedCount / 1000);
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    /** The bar's fill, 0 to 1 (finished / total). */
    static synchronized float progress() {
        if (run == null) return 0f;
        if (run.ended) return 1f;
        return run.total == 0 ? 0f : Math.min(1f, run.finishedCount / (float) run.total);
    }

    /** 0 green, 1 yellow, 2 red. */
    static synchronized int state() { return run == null ? 0 : run.fail + run.crash > 0 ? 2 : run.slow > 0 ? 1 : 0; }

    private static String stepNumbers() {
        return "step " + (run.total == 0 ? run.index + "/?" : Math.min(run.index, run.total) + "/" + run.total);
    }

    private static String counts(String sep) {
        return "OK " + run.ok + " · SLOW " + run.slow + " · FAIL " + run.fail + " · CRASH " + run.crash + sep + "ETA " + eta();
    }

    /** The full one-line text. */
    static synchronized String text() {
        return "Lads QA · " + run.name + " · " + stepNumbers() + " · " + run.label + " · " + counts(" · ");
    }

    /**
     * Draws the bar at the top centre in the game's GUI units, after everything else; nothing when there is no run or the bar is
     * hidden. Long text goes on two lines (name and step above, counts and ETA below), the step label cut to fit.
     */
    public static synchronized void draw(LadsGraphics g) {
        if (!on || run == null || g == null || g.getScaledWidth() <= 0) return;
        if (g.getGame() != null && g.getGame().isHudHidden()) return;
        if (hideFrames > 0) { hideFrames--; return; }
        if (hidden) return;
        int w = g.getScaledWidth(), room = w - 8, y = 2;
        String line = text();
        String second = null;
        if (g.textWidth(line) > room) {
            String head = "Lads QA · " + run.name + " · " + stepNumbers() + " · ", shown = run.label;
            line = head + shown;
            second = counts("   ");
            while (g.textWidth(line) > room && shown.length() > 1) {
                shown = shown.substring(0, shown.length() - 2) + "~";
                line = head + shown;
            }
        }
        g.drawCenteredText(line, w / 2, y, 0xFFFFFFFF, true);
        y += g.fontHeight() + 1;
        if (second != null) {
            g.drawCenteredText(second, w / 2, y, 0xFFFFFFFF, true);
            y += g.fontHeight() + 1;
        }
        int bar = 182, x = (w - bar) / 2;
        int[] fills = {0xFF3FB83F, 0xFFE8C422, 0xFFD83A3A}; // green, yellow, red
        int[] shine = {0xFF7BE07B, 0xFFFFE566, 0xFFFF7A7A};
        int s = state();
        g.fill(x - 1, y - 1, x + bar + 1, y + 6, 0xFF000000);
        g.fill(x, y, x + bar, y + 5, 0xFF4A4A4A);
        if (run.total == 0 && !run.ended) { // not known: stripes (still, so the frames do not change under a pixel check)
            for (int at = 0; at < bar; at += 20) {
                g.fill(x + at, y, x + Math.min(bar, at + 10), y + 5, fills[s]);
                g.fill(x + at, y, x + Math.min(bar, at + 10), y + 1, shine[s]);
            }
            return;
        }
        int to = Math.round(bar * progress());
        if (to > 0) {
            g.fill(x, y, x + to, y + 5, fills[s]);
            g.fill(x, y, x + to, y + 1, shine[s]);
        }
    }

    /** Test hooks: another clock, the bar on or off, a clean slate. */
    static synchronized void reset(boolean enabled, LongSupplier time) {
        on = enabled;
        clock = time;
        run = null;
        OUTER.clear();
        hideFrames = 0;
        hidden = finalShotDue = false;
    }
}
