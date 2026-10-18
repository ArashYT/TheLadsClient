package com.thelads.core.client.qa;

import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.bridge.LadsGraphics;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * QA only (permanent owner rule: every QA, the whole time, shows a live progress bar), as a boss-bar-style bar at the top centre,
 * drawn on whatever is up (HUD, screens, loading overlay) by the versions' draw hooks ({@link #draw}). Two views:
 * <ul>
 * <li><b>a run</b> ({@link #begin}, {@link #step}, {@link #done}): "Lads QA - run - step i/N - current - OK n - SLOW n - FAIL n -
 * CRASH n - ETA m:ss". Green while clean, yellow once a step was SLOW, red on a FAIL or CRASH; the ETA is the average finished step
 * time times the steps left. A run begun inside another is nested in it (the bar shows the inner one, the outer one's open step takes
 * its worst result). A finished outermost run stays up for a few seconds with its final state.</li>
 * <li><b>the session</b>, from game start to exit, whenever no run is up: "Lads QA - name - phase - probes k/N - elapsed m:ss" with
 * the phase loading / world ready / waiting for the next probe / finishing. The harness passes the name (thelads.qaRun) and the list
 * of required probes (thelads.qaProbes, "a|b|c"); it reports the passed ones and the end in the file thelads.qaProgress
 * ("passed=a|b", "failed=n", "finishing=1").</li>
 * </ul>
 * Active only when the Verification harness started the game ({@code -Dthelads.qaBar=1}, or LADS_QA_BAR in the environment); for
 * players every call is a no-op on a constant. The strings are built when something changes (a step, a second), never per frame. A
 * check that compares HUD pixels hides the bar first: {@link #hideFrames} or {@link #hide}.
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

    private static boolean on = flag(setting("thelads.qaBar", "LADS_QA_BAR"));
    private static LongSupplier clock = System::currentTimeMillis;
    private static Run run;
    private static final ArrayDeque<Run> OUTER = new ArrayDeque<>();
    private static int hideFrames, rev;
    private static boolean hidden, finalShotDue;
    private static long endedAt;
    /** How long a finished outermost run's final state stays up before the session view returns. */
    private static final long LINGER_MS = 3_000;

    // The session: game start to exit.
    private static final String NAME = orElse(setting("thelads.qaRun", "LADS_QA_RUN"), "QA");
    private static final String[] PROBES = splitProbes(setting("thelads.qaProbes", "LADS_QA_PROBES"));
    private static final Path PROGRESS = progressFile(setting("thelads.qaProgress", "LADS_QA_PROGRESS"));
    private static long started = jvmStart(), progressChecked, progressStamp;
    private static Set<String> probesPassed = Set.of();
    private static int probesDone, harnessFailed, sessionSlow, sessionFail, sessionRuns;
    private static boolean finishing;
    private static String nextProbe = "", waiting = "";

    // What the last draw built (rebuilt only when one of these changed).
    private static final String LOADING = "loading", READY = "world ready", FINISHING = "finishing";
    private static String cLine1 = "", cLine2, cPhase;
    private static boolean cRun, cLoading;
    private static int cRev = -1, cWidth = -1, cHalf, cState;
    private static long cSecond = -1;
    private static float cFill;
    private static boolean cStripes;
    private static Object cMetrics;

    private QaProgress() {}

    private static String setting(String property, String variable) {
        String value = System.getProperty(property);
        return value != null ? value : System.getenv(variable);
    }

    private static String orElse(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }

    private static boolean flag(String value) {
        return value != null && !value.isEmpty() && !value.equals("0") && !value.equalsIgnoreCase("false");
    }

    private static String[] splitProbes(String list) { return list == null || list.isBlank() ? new String[0] : list.split("\\|"); }

    private static Path progressFile(String path) {
        try { return path == null || path.isBlank() ? null : Path.of(path); } catch (RuntimeException invalid) { return null; }
    }

    private static long jvmStart() {
        try { return ManagementFactory.getRuntimeMXBean().getStartTime(); } catch (RuntimeException | LinkageError unavailable) { return System.currentTimeMillis(); }
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
        rev++;
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
        rev++;
    }

    /** A check: the next step, named for it, failed when it did not hold (the one before passes). */
    public static synchronized void check(String what, boolean ok) {
        step(what);
        if (!ok) result(Result.FAIL);
    }

    /** Renames the open step (a step that learns what it is doing as it goes). */
    public static synchronized void label(String stepLabel) {
        if (on && run != null && run.open) { run.label = stepLabel == null ? "" : stepLabel; rev++; }
    }

    /** Ends the open step with this result (a later {@link #step} then does not pass it again); with none open, counts one more of it. */
    public static synchronized void result(Result result) {
        if (!on || run == null) return;
        if (run.open) finish(run, result);
        else count(run, result);
        rev++;
    }

    /** Ends the run: the open step passes; the bar stays with the final counts and fills (a nested run hands its worst result to the run around it). */
    public static synchronized void done() {
        if (!on || run == null || run.ended) return;
        finish(run, null);
        run.ended = true;
        run.label = "done";
        rev++;
        if (OUTER.isEmpty()) {
            finalShotDue = true;
            endedAt = clock.getAsLong();
            sessionRuns++;
            sessionFail += run.fail + run.crash;
            sessionSlow += run.slow;
            return;
        }
        Run inner = run;
        run = OUTER.pop();
        Result worst = inner.crash > 0 ? Result.CRASH : inner.fail > 0 ? Result.FAIL : inner.slow > 0 ? Result.SLOW : null;
        if (worst != null) result(worst);
    }

    /**
     * A quarter of a second after the outermost run is done (the game then has drawn the final state), once: the name of the
     * screenshot ("qa-bar-final-&lt;run&gt;") that shows the bar's final state, which the version's QA driver then saves; null otherwise.
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
        return mss(seconds);
    }

    private static String mss(long seconds) { return (seconds / 60) + ":" + (seconds % 60 < 10 ? "0" : "") + (seconds % 60); }

    /** The run bar's fill, 0 to 1 (finished / total). */
    static synchronized float progress() {
        if (run == null) return 0f;
        if (run.ended) return 1f;
        return run.total == 0 ? 0f : Math.min(1f, run.finishedCount / (float) run.total);
    }

    /** 0 green, 1 yellow, 2 red (of the run showing). */
    static synchronized int state() { return run == null ? 0 : run.fail + run.crash > 0 ? 2 : run.slow > 0 ? 1 : 0; }

    private static String stepNumbers() {
        return "step " + (run.total == 0 ? run.index + "/?" : Math.min(run.index, run.total) + "/" + run.total);
    }

    private static String counts(String sep) {
        return "OK " + run.ok + " · SLOW " + run.slow + " · FAIL " + run.fail + " · CRASH " + run.crash + sep + "ETA " + eta();
    }

    /** The run's full one-line text. */
    static synchronized String text() {
        return "Lads QA · " + run.name + " · " + stepNumbers() + " · " + run.label + " · " + counts(" · ");
    }

    // ---- the session --------------------------------------------------------------------------------------------------------------

    /** Reads the harness's progress file when it changed (at most twice a second). */
    private static void refresh(long now) {
        if (PROGRESS == null || now - progressChecked < 500) return;
        progressChecked = now;
        try {
            long stamp = Files.getLastModifiedTime(PROGRESS).toMillis();
            if (stamp == progressStamp) return;
            progressStamp = stamp;
            Set<String> passed = new HashSet<>();
            int failed = 0;
            boolean end = false;
            for (String line : Files.readAllLines(PROGRESS)) {
                if (line.startsWith("passed=")) for (String probe : line.substring(7).split("\\|")) { if (!probe.isEmpty()) passed.add(probe); }
                else if (line.startsWith("failed=")) failed = parseInt(line.substring(7));
                else if (line.startsWith("finishing=")) end = !line.endsWith("=0");
            }
            probesPassed = passed;
            harnessFailed = failed;
            finishing = end;
            probesDone = 0;
            nextProbe = "";
            for (String probe : PROBES) {
                if (passed.contains(probe)) probesDone++;
                else if (nextProbe.isEmpty()) nextProbe = probe;
            }
            waiting = nextProbe.isEmpty() ? "" : "waiting for " + nextProbe;
            rev++;
        } catch (IOException | RuntimeException unreadable) {
            // not written yet, or being replaced: the next look
        }
    }

    private static int parseInt(String value) {
        try { return Integer.parseInt(value.trim()); } catch (NumberFormatException invalid) { return 0; }
    }

    private static String phase(boolean inGame) {
        return finishing ? FINISHING : !inGame ? LOADING : waiting.isEmpty() ? READY : waiting;
    }

    // ---- drawing ------------------------------------------------------------------------------------------------------------------

    /**
     * Draws the bar at the top centre in the game's GUI units, after everything else; nothing when it is hidden. Long text goes on two
     * lines (name and step above, counts and ETA below), the step label cut to fit.
     */
    public static void draw(LadsGraphics g) { draw(g, false); }

    /**
     * The same over the loading screen (startup, resource reloads): no text (the game's font is still being loaded then and draws
     * boxes) and a striped bar, which says "loading" until the world is up and the text is readable.
     */
    public static void drawLoading(LadsGraphics g) { draw(g, true); }

    private static synchronized void draw(LadsGraphics g, boolean loading) {
        if (!on || g == null || g.getScaledWidth() <= 0) return;
        LadsGameBridge game = g.getGame();
        if (game != null && game.isHudHidden()) return;
        if (hideFrames > 0) { hideFrames--; return; }
        if (hidden) return;
        long now = clock.getAsLong();
        refresh(now);
        boolean showRun = !loading && run != null && (!run.ended || now - endedAt < LINGER_MS);
        int w = g.getScaledWidth();
        String phase = showRun ? null : phase(game != null && game.isIngame());
        long second = showRun ? 0 : (now - started) / 1000;
        Object metrics = g.textMetricsKey();
        if (cRev != rev || cRun != showRun || cWidth != w || cSecond != second || cPhase != phase || cMetrics != metrics || cLoading != loading) {
            cRev = rev; cRun = showRun; cWidth = w; cSecond = second; cPhase = phase; cMetrics = metrics; cLoading = loading;
            build(g, w, showRun, phase, second);
            if (loading) { cLine1 = null; cLine2 = null; cStripes = true; cHalf = 95; }
        }
        int y = 2, fh = g.fontHeight() + 1, bar = 182, x = (w - bar) / 2;
        if (cLine1 == null) { // loading screen: just the bar
            g.fill(w / 2 - cHalf, 0, w / 2 + cHalf, y + 12, 0x90000000);
        } else {
            g.fill(w / 2 - cHalf, 0, w / 2 + cHalf, y + (cLine2 == null ? 1 : 2) * fh + 8, 0x90000000);
            g.drawCenteredText(cLine1, w / 2, y, 0xFFFFFFFF, true);
            y += fh;
            if (cLine2 != null) {
                g.drawCenteredText(cLine2, w / 2, y, 0xFFFFFFFF, true);
                y += fh;
            }
        }
        int fill = cState == 2 ? 0xFFD83A3A : cState == 1 ? 0xFFE8C422 : 0xFF3FB83F; // red, yellow, green
        int shine = cState == 2 ? 0xFFFF7A7A : cState == 1 ? 0xFFFFE566 : 0xFF7BE07B;
        g.fill(x - 1, y - 1, x + bar + 1, y + 6, 0xFF000000);
        g.fill(x, y, x + bar, y + 5, 0xFF4A4A4A);
        if (cStripes) { // not known: stripes (still, so the frames do not change under a pixel check)
            for (int at = 0; at < bar; at += 20) {
                g.fill(x + at, y, x + Math.min(bar, at + 10), y + 5, fill);
                g.fill(x + at, y, x + Math.min(bar, at + 10), y + 1, shine);
            }
            return;
        }
        int to = Math.round(bar * cFill);
        if (to > 0) {
            g.fill(x, y, x + to, y + 5, fill);
            g.fill(x, y, x + to, y + 1, shine);
        }
    }

    /** Builds what the next frames draw: the text lines (wrapped to the width), the backdrop, the colour and the fill. */
    private static void build(LadsGraphics g, int w, boolean showRun, String phase, long second) {
        int room = w - 8;
        String line, second2 = null;
        if (showRun) {
            line = text();
            if (g.textWidth(line) > room) {
                String head = "Lads QA · " + run.name + " · " + stepNumbers() + " · ", shown = run.label;
                line = head + shown;
                second2 = counts("   ");
                while (g.textWidth(line) > room && shown.length() > 1) {
                    shown = shown.substring(0, shown.length() - 2) + "~";
                    line = head + shown;
                }
            }
            cState = state();
            cFill = progress();
            cStripes = run.total == 0 && !run.ended;
        } else {
            String probes = PROBES.length > 0 ? "probes " + probesDone + "/" + PROBES.length : "runs " + sessionRuns;
            line = "Lads QA · " + NAME + " · " + phase + " · " + probes + " · elapsed " + mss(second);
            if (g.textWidth(line) > room) {
                line = "Lads QA · " + NAME + " · " + phase;
                second2 = probes + "   elapsed " + mss(second);
            }
            cState = harnessFailed + sessionFail > 0 ? 2 : sessionSlow > 0 ? 1 : 0;
            cFill = PROBES.length > 0 ? probesDone / (float) PROBES.length : 0f;
            cStripes = PROBES.length == 0;
        }
        cLine1 = line;
        cLine2 = second2;
        // A dark backdrop behind the text and the bar, so they read over whatever the Lads HUD or the game has drawn there.
        cHalf = Math.max(182, Math.max(g.textWidth(line), second2 == null ? 0 : g.textWidth(second2))) / 2 + 4;
    }

    /** Test hooks: another clock, the bar on or off, a clean slate. */
    static synchronized void reset(boolean enabled, LongSupplier time) {
        on = enabled;
        clock = time;
        run = null;
        OUTER.clear();
        hideFrames = 0;
        hidden = finalShotDue = false;
        rev++;
        started = time.getAsLong();
        sessionRuns = sessionFail = sessionSlow = 0;
        finishing = false;
        endedAt = 0;
    }
}
