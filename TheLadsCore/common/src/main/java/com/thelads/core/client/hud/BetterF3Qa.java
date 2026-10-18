package com.thelads.core.client.hud;

import com.google.gson.JsonElement;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import com.thelads.core.modules.BetterF3Module;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only, the same on every version (the HUD layout probe's driver runs it instead of HudLayoutQa when LADS_QA_F3_1911=1 or
 * -Dthelads.qaF3=true): 1.9.11's Better F3. The default look at GUI scale 2 and 1 (f3-1911-default-*.png, to compare with the
 * owner's BetterF3 screenshot), with Other Lines on, in the old style; then F3 toggled every 100 ms with a frame each 50 ms
 * (f3-1911-spam-NN.png) and every 50 ms, while every drawn frame of both columns is traced (time, open, progress, x of the first and
 * last line): no frame may move a column more than one 50 ms step, F3 left open must end fully in, closed fully out. Markers as
 * HudCoverQa's ("Lads hud layout check PASS/FAIL", "Lads hud layout END"), so the harness waits for it the same way.
 */
public final class BetterF3Qa {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final List<String> FAILURES = new ArrayList<>();
    private static final Map<Option, JsonElement> SAVED = new LinkedHashMap<>();
    private static final List<double[]> TRACE = new ArrayList<>();
    private static int step = -1, wait, passed, spam, toggles, reversals;
    private static boolean open, enabledWas;
    private static long started;

    private BetterF3Qa() {}

    public static boolean wanted() {
        return "1".equals(System.getenv("LADS_QA_F3_1911")) || Boolean.getBoolean("thelads.qaF3");
    }

    public static int failed() { return FAILURES.size(); }

    private static BetterF3Module f3() { return (BetterF3Module) ModuleManager.getInstance().getModule("BetterF3"); }

    /** Each client tick of the probe; true once the sequence has ended. */
    public static boolean tick(HudLayoutQa.Driver d) {
        try {
            if (step == -1) { begin(d); com.thelads.core.client.qa.QaProgress.begin("Better F3", 24); step = 0; wait = 40; return false; }
            if (wait > 0) { wait--; return false; }
            if (step < 24) com.thelads.core.client.qa.QaProgress.step("Better F3 " + (step + 1));
            switch (step++) {
                // F3 closed: the Lads HUD (and on 1.8.9 Xaero's map) is shown.
                case 0 -> shot(d, "f3-1911-f3-closed");
                case 1 -> { d.debug(open = true); wait = 40; }
                case 2 -> shot(d, "f3-1911-default-gui2");
                case 3 -> { d.guiScale(1); wait = 20; }
                case 4 -> shot(d, "f3-1911-default-gui1");
                case 5 -> { bool("Show Other Lines", true); wait = 10; }
                case 6 -> { shot(d, "f3-1911-other-lines-gui1"); bool("Show Other Lines", false); }
                case 7 -> { style(BetterF3Module.LADS); wait = 10; }
                case 8 -> { shot(d, "f3-1911-old-style-gui1"); style(BetterF3Module.BETTERF3); d.guiScale(2); wait = 20; }
                case 9 -> { d.debug(open = false); wait = 2; }
                // 100 ms into the slide out: the lines half out, the Lads HUD still aside.
                case 10 -> {
                    expect("sliding out: the Lads HUD stays aside", f3().closing() && BetterF3Module.hidesLadsHud(false), "closing " + f3().closing());
                    d.quickShot("f3-1911-sliding-out");
                    wait = 20;
                }
                case 11 -> {
                    expect("closed: both columns slid out and were forgotten, the Lads HUD back", !f3().closing() && !BetterF3Module.hidesLadsHud(false),
                        "progress L " + f3().progress(true) + ", R " + f3().progress(false));
                    TRACE.clear();
                    started = System.nanoTime();
                    BetterF3Module.trace = BetterF3Qa::trace;
                    spam = 0;
                }
                // F3 every 100 ms (two ticks) for 2.2 s, a frame each tick: 11 toggles, so it ends open.
                case 12 -> {
                    if (spam % 2 == 0 && spam < 22) { d.debug(open = !open); toggles++; }
                    d.quickShot(String.format(Locale.ROOT, "f3-1911-spam-%02d", spam));
                    if (++spam < 26) step = 12;
                }
                case 13 -> { wait = 20; }
                case 14 -> {
                    expect("left open after spamming: in place", open && f3().progress(true) == 1 && f3().progress(false) == 1,
                        "open " + open + ", progress L " + f3().progress(true) + ", R " + f3().progress(false));
                    shot(d, "f3-1911-after-spam-open");
                }
                case 15 -> { d.debug(open = false); wait = 20; }
                case 16 -> {
                    expect("closed after spamming: slid out", !f3().closing(), "progress L " + f3().progress(true) + ", R " + f3().progress(false));
                    shot(d, "f3-1911-after-spam-closed");
                    spam = 0;
                }
                // F3 every 50 ms (each tick), 8 times: ends closed.
                case 17 -> { d.debug(open = !open); toggles++; if (++spam < 8) step = 17; }
                case 18 -> { wait = 20; }
                case 19 -> {
                    expect("closed after spamming every 50 ms: slid out, never stuck", !open && !f3().closing(),
                        "open " + open + ", progress L " + f3().progress(true) + ", R " + f3().progress(false));
                    BetterF3Module.trace = null;
                    analyse();
                }
                // Better F3 off: the game's own debug text, and nothing of the Lads HUD (or Xaero's map) steps aside.
                case 20 -> { f3().setEnabled(false); d.debug(open = true); wait = 20; }
                case 21 -> {
                    expect("Better F3 off: nothing hides the Lads HUD under F3", !BetterF3Module.hidesLadsHud(true), "hides");
                    shot(d, "f3-1911-betterf3-off-f3-open");
                }
                case 22 -> { d.debug(open = false); wait = 10; }
                case 23 -> { shot(d, "f3-1911-betterf3-off-f3-closed"); f3().setEnabled(true); }
                default -> { end(d); return true; }
            }
        } catch (RuntimeException failure) {
            FAILURES.add("step " + step + ": " + failure); com.thelads.core.client.qa.QaProgress.result(com.thelads.core.client.qa.QaProgress.Result.FAIL);
            LOGGER.error("Lads hud layout FAILED at Better F3 step {}", step, failure);
            end(d);
            return true;
        }
        return false;
    }

    private static void trace(boolean left, boolean shown, double progress, int firstX, int lastX) {
        if (TRACE.size() > 20_000) return;
        TRACE.add(new double[] {(System.nanoTime() - started) / 1e6, left ? 1 : 0, shown ? 1 : 0, progress, firstX, lastX});
    }

    /** The traced frames: no column moves more than one 50 ms step between two frames, and the slide turned around as F3 did. */
    private static void analyse() {
        double limit = 0.05 / (BetterF3Module.SLIDE_SECONDS) + 1e-6, worst = 0;
        StringBuilder csv = new StringBuilder("ms,side,open,progress,first_x,last_x\n");
        for (int side = 0; side <= 1; side++) {
            double[] last = null;
            int direction = 0;
            for (double[] frame : TRACE) {
                if (frame[1] != side) continue;
                if (last != null && frame[0] - last[0] < 150) {
                    double delta = frame[3] - last[3];
                    worst = Math.max(worst, Math.abs(delta));
                    int now = delta > 1e-9 ? 1 : delta < -1e-9 ? -1 : 0;
                    if (now != 0 && direction != 0 && now != direction) reversals++;
                    if (now != 0) direction = now;
                }
                last = frame;
            }
        }
        for (double[] frame : TRACE)
            csv.append(String.format(Locale.ROOT, "%.1f,%s,%d,%.4f,%d,%d\n", frame[0], frame[1] == 1 ? "L" : "R", (int) frame[2], frame[3], (int) frame[4], (int) frame[5]));
        LOGGER.info("Lads Better F3 trace ({} frames, {} toggles):\n{}", TRACE.size(), toggles, csv);
        expect("traced " + TRACE.size() + " column frames while F3 was toggled " + toggles + " times", TRACE.size() > 40, "too few frames");
        expect(String.format(Locale.ROOT, "no frame moved a column more than one 50 ms step (worst %.4f, limit %.4f)", worst, limit), worst <= limit, "a pop");
        expect("the slide turned around where it was (" + reversals + " reversals)", reversals >= 6, "too few reversals");
    }

    private static void shot(HudLayoutQa.Driver d, String name) {
        d.shot(name);
        com.thelads.core.client.qa.QaProgress.label(name);
        LOGGER.info("Lads hud layout frame {}", name);
        wait = 10;
    }

    private static void bool(String name, boolean value) { ((BoolOption) f3().getOption(name)).set(value); }
    private static void style(int index) { ((DropdownOption) f3().getOption("Style")).setIndex(index); }

    private static void expect(String what, boolean ok, String detail) {
        if (ok) { passed++; LOGGER.info("Lads hud layout check PASS: Better F3: {}", what); }
        else { FAILURES.add(what + " (" + detail + ")"); com.thelads.core.client.qa.QaProgress.result(com.thelads.core.client.qa.QaProgress.Result.FAIL); LOGGER.error("Lads hud layout check FAIL: Better F3: {} ({})", what, detail); }
    }

    private static void begin(HudLayoutQa.Driver d) {
        BetterF3Module f3 = f3();
        enabledWas = f3.isEnabled();
        for (Option option : f3.getOptions()) SAVED.put(option, option.save().deepCopy());
        f3.getOptions().forEach(Option::reset);
        f3.setEnabled(true);
        d.debug(false);
        d.guiScale(2); // the owner's screenshot was at GUI scale 2
        for (String rule : new String[] {"sendCommandFeedback", "send_command_feedback", "logAdminCommands", "log_admin_commands"}) d.command("gamerule " + rule + " false");
        d.command("time set 3814"); // the owner's screenshot: day 0, tick 3814
        d.command("tp @a ~ ~ ~ 90 30"); // facing west, looking at the ground: a targeted block
        LOGGER.info("Lads hud layout BEGIN: Better F3 1.9.11 sequence");
    }

    private static void end(HudLayoutQa.Driver d) {
        step = 99;
        BetterF3Module.trace = null;
        try {
            d.debug(false);
            d.guiScale(2);
            SAVED.forEach((option, value) -> option.load(value));
            f3().setEnabled(enabledWas);
            for (String rule : new String[] {"sendCommandFeedback", "send_command_feedback", "logAdminCommands", "log_admin_commands"}) d.command("gamerule " + rule + " true");
        } catch (RuntimeException failure) {
            FAILURES.add("restoring: " + failure);
        }
        if (!FAILURES.isEmpty()) LOGGER.error("Lads hud layout FAILED: {}", String.join(" | ", FAILURES));
        com.thelads.core.client.qa.QaProgress.done();
        LOGGER.info("Lads hud layout END: {} passed, {} failed", passed, FAILURES.size());
    }
}
