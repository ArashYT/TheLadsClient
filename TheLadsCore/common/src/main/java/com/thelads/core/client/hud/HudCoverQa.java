package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only, the same on every version (the HUD layout probe's driver runs it instead of HudLayoutQa when LADS_QA_HUDCOVER=1 or
 * -Dthelads.qaHudCover=true): the 1.9.0 HUD cover sequence. Day is placed under the tab list, CPS and Memory under the debug
 * text's left and right columns, FPS in the middle of the screen. With the HUD FPS cap off and then on, the tab list and F3 (Better
 * F3, then the game's plain text) are opened and closed; on the first Lads HUD frame after each, the covered widgets must be left out
 * (HudManager.coveredHuds) and the rest drawn. Frames go to screenshots/cover-*.png; lines "Lads hud layout check PASS/FAIL: cover
 * ...", ending with "Lads hud layout END: N passed, M failed" (the HUD layout probe's markers, so the harness waits for it the same way).
 */
public final class HudCoverQa {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final List<String> OURS = new ArrayList<>(List.of("Day", "CPS", "Memory", "FPS"));
    /** Xaero's map (Zero SMP, 26.3 when it is there): placed under the tab list too, beside Day. */
    private static boolean minimap;
    private static int step = -1, wait, passed, pass;
    private static final List<String> FAILURES = new ArrayList<>();
    private static final Map<String, int[]> POSITIONS_WERE = new HashMap<>();
    private static final Map<String, HudSettings.Anchor> ANCHORS_WERE = new HashMap<>();
    private static final List<Set<String>> GROUPS_WERE = new ArrayList<>();
    private static final Map<Module, Boolean> ENABLED_WERE = new LinkedHashMap<>();
    private static boolean capWas, hideWas;
    private static int limitWas, scaleWas;

    private HudCoverQa() {}

    /** Whether the HUD layout probe runs this sequence instead. */
    public static boolean wanted() {
        return "1".equals(System.getenv("LADS_QA_HUDCOVER")) || Boolean.getBoolean("thelads.qaHudCover");
    }

    public static int failed() { return FAILURES.size(); }

    /** Each client tick of the probe; true once the sequence has ended. */
    public static boolean tick(HudLayoutQa.Driver d) {
        try {
            if (step == -1) { begin(d); com.thelads.core.client.qa.QaProgress.begin("HUD cover (tab list, F3)", 34); step = -2; wait = 40; return false; }
            if (wait > 0) { wait--; return false; }
            if (step == -2) { d.guiScale(2); step = 0; wait = 40; return false; }
            String cap = pass == 0 ? "capoff" : "capon";
            if (step < 17) com.thelads.core.client.qa.QaProgress.step(cap + " " + (step + 1) + "/17");
            switch (step++) {
                case 0 -> {
                    HudSettings.getInstance().setHudFpsCapEnabled(pass == 1);
                    HudSettings.getInstance().setHudFpsLimit(pass == 1 ? 30 : limitWas);
                    if (pass == 0) afterFrame("placing the widgets", HudCoverQa::place);
                    wait = 20;
                }
                case 1 -> { check("nothing open, HUD FPS cap " + cap, Set.of(), true); wait = 10; }
                case 2 -> shot(d, "cover-" + pass + "1-closed-" + cap);
                // The first Lads HUD frame after the key: the tab list covers Day at once (a capped HUD is rebuilt for it).
                case 3 -> { d.tab(true); check("tab list open, first frame, cap " + cap, underTab(), true); wait = 10; }
                case 4 -> { check("tab list open, cap " + cap, underTab(), true); wait = 5; }
                case 5 -> shot(d, "cover-" + pass + "2-tab-" + cap);
                case 6 -> { d.tab(false); check("tab list closed, first frame, cap " + cap, Set.of(), true); wait = 10; }
                case 7 -> {
                    module("BetterF3", true);
                    option("BetterF3", "Hide Lads HUD", false);
                    d.debug(true);
                    wait = 50; // Better F3's lines slide in
                }
                case 8 -> { check("F3 (Better F3, Hide Lads HUD off), cap " + cap, Set.of("CPS", "Memory"), false); wait = 5; }
                case 9 -> shot(d, "cover-" + pass + "3-f3-betterf3-" + cap);
                case 10 -> { module("BetterF3", false); wait = 10; }
                case 11 -> { check("F3 (the game's own text), cap " + cap, Set.of("CPS", "Memory"), false); wait = 5; }
                case 12 -> shot(d, "cover-" + pass + "4-f3-plain-" + cap);
                case 13 -> { d.debug(false); check("F3 closed, first frame, cap " + cap, Set.of(), true); wait = 10; }
                case 14 -> {
                    module("BetterF3", true);
                    option("BetterF3", "Hide Lads HUD", true);
                    d.debug(true);
                    wait = 20;
                }
                case 15 -> shot(d, "cover-" + pass + "5-f3-hide-lads-hud-" + cap);
                case 16 -> {
                    d.debug(false);
                    check("F3 closed after Hide Lads HUD, first frame, cap " + cap, Set.of(), true);
                    wait = 10;
                    if (pass == 0) { pass = 1; step = 0; }
                }
                default -> { end(d); return true; }
            }
        } catch (RuntimeException failure) {
            FAILURES.add("step " + step + ": " + failure); com.thelads.core.client.qa.QaProgress.result(com.thelads.core.client.qa.QaProgress.Result.FAIL);
            LOGGER.error("Lads hud layout FAILED at cover step {}", step, failure);
            end(d);
            return true;
        }
        return false;
    }

    private static void shot(HudLayoutQa.Driver d, String name) {
        d.shot(name);
        com.thelads.core.client.qa.QaProgress.label(name);
        LOGGER.info("Lads hud layout frame {}", name);
        wait = 10;
    }

    private static Module module(String name, boolean enabled) {
        Module module = ModuleManager.getInstance().getModule(name);
        if (module == null) throw new IllegalStateException("no module " + name);
        module.setEnabled(enabled);
        return module;
    }

    private static void option(String module, String name, boolean value) {
        if (!(ModuleManager.getInstance().getModule(module).getOption(name) instanceof BoolOption option)) throw new IllegalStateException("no option " + module + "/" + name);
        option.set(value);
    }

    private static void begin(HudLayoutQa.Driver d) {
        HudSettings settings = HudSettings.getInstance();
        POSITIONS_WERE.putAll(settings.getPositions());
        ANCHORS_WERE.putAll(settings.getAnchors());
        settings.getGroups().forEach(group -> GROUPS_WERE.add(Set.copyOf(group)));
        scaleWas = settings.getHudScale();
        capWas = settings.isHudFpsCapEnabled();
        limitWas = settings.getHudFpsLimit();
        hideWas = ModuleManager.getInstance().getModule("BetterF3").getOption("Hide Lads HUD") instanceof BoolOption hide && hide.get();
        minimap = HudManager.getInstance().getElements().stream().anyMatch(e -> "Minimap".equals(e.getModuleName()) && e.isAvailable());
        if (minimap && !OURS.contains("Minimap")) OURS.add("Minimap");
        // Only the widgets of the sequence, so nothing else sits under the tab list or F3 by chance.
        for (HudElement element : HudManager.getInstance().getElements()) {
            Module module = element.getModuleName() == null ? null : ModuleManager.getInstance().getModule(element.getModuleName());
            if (module == null) continue;
            ENABLED_WERE.putIfAbsent(module, module.isEnabled());
            module.setEnabled(OURS.contains(module.getName()));
        }
        for (String name : new String[] {"Autohide", "BetterF3"}) {
            Module module = ModuleManager.getInstance().getModule(name);
            if (module != null) ENABLED_WERE.putIfAbsent(module, module.isEnabled());
        }
        module("Autohide", false); // it would fade the Lads HUD out while the QA player stands still
        settings.clearPositions();
        settings.getAnchors().clear();
        settings.replaceGroups(List.of());
        settings.setHudScale(0);
        d.window(1600, 900);
        for (String rule : new String[] {"sendCommandFeedback", "send_command_feedback", "logAdminCommands", "log_admin_commands"}) d.command("gamerule " + rule + " false");
        d.command("gamemode creative @a");
        d.effect(null, 0);
        d.effect("speed", 1); // vanilla's effect icon in the top right, under F3's right column
        // A lone player's tab list shows only with an objective in the list slot.
        d.command("scoreboard objectives add ladsqa dummy");
        d.command("scoreboard objectives setdisplay list ladsqa");
        d.command("scoreboard players set @a ladsqa 7");
        LOGGER.info("Lads hud layout BEGIN: cover sequence");
    }

    /** Day under the tab list (top centre), CPS and Memory under the debug text's columns, FPS in the middle, clear of both. */
    private static void place(LadsGraphics g) {
        HudSettings settings = HudSettings.getInstance();
        int width = g.getScaledWidth(), height = g.getScaledHeight();
        settings.setPosition("Day", width / 2 - 60, 12);
        if (minimap) settings.setPosition("Minimap", width / 2 + 5, 12);
        settings.setPosition("CPS", 6, 40);
        settings.setPosition("Memory", width - 70, 40);
        settings.setPosition("FPS", width / 2 - 25, height / 2 + 20);
        LOGGER.info("Lads hud layout cover widgets placed at {}x{}: {}", width, height, settings.getPositions().keySet());
    }

    private static Set<String> underTab() {
        return minimap ? Set.of("Day", "Minimap") : Set.of("Day");
    }

    /** On the next Lads HUD frame: exactly these of the sequence's widgets are left out (the map's state only where {@code mapKnown}). */
    private static void check(String label, Set<String> covered, boolean mapKnown) {
        HudManager.afterRender = g -> {
            try {
                var hud = HudManager.getInstance();
                Set<String> now = new java.util.TreeSet<>(hud.coveredHuds());
                now.retainAll(OURS);
                Set<String> compared = new java.util.TreeSet<>(now);
                if (!mapKnown) compared.remove("Minimap");
                StringBuilder boxes = new StringBuilder();
                for (int[] box : HudCover.boxes())
                    boxes.append(box[0] == HudCover.TAB ? "tab" : "f3").append('[').append(box[1]).append(',').append(box[2]).append(',').append(box[3]).append(',').append(box[4]).append("] ");
                StringBuilder at = new StringBuilder();
                for (String name : OURS) at.append(name).append('=').append(hud.lastBounds(name)).append(' ');
                LOGGER.info("Lads hud cover {}: covered {} (HUD builds {}), widgets {}, boxes {}", label, now, HudFrameCap.builds, at.toString().trim(), boxes.toString().trim());
                expect(label, "covered widgets are " + covered + (mapKnown ? "" : " (the map not checked)"), compared.equals(covered), "covered " + now);
            } catch (RuntimeException failure) {
                FAILURES.add(label + ": " + failure);
                LOGGER.error("Lads hud layout check FAIL: cover: {}", label, failure);
            }
        };
    }

    private static void afterFrame(String label, java.util.function.Consumer<LadsGraphics> action) {
        HudManager.afterRender = g -> {
            try { action.accept(g); }
            catch (RuntimeException failure) { FAILURES.add(label + ": " + failure); com.thelads.core.client.qa.QaProgress.result(com.thelads.core.client.qa.QaProgress.Result.FAIL); LOGGER.error("Lads hud layout check FAIL: cover: {}", label, failure); }
        };
    }

    private static void expect(String label, String what, boolean ok, String detail) {
        if (ok) { passed++; LOGGER.info("Lads hud layout check PASS: cover: {}: {}", label, what); }
        else { FAILURES.add(label + ": " + what + " (" + detail + ")"); com.thelads.core.client.qa.QaProgress.result(com.thelads.core.client.qa.QaProgress.Result.FAIL); LOGGER.error("Lads hud layout check FAIL: cover: {}: {} ({})", label, what, detail); }
    }

    private static void end(HudLayoutQa.Driver d) {
        step = 99;
        HudManager.afterRender = null;
        try {
            d.tab(false);
            d.debug(false);
            HudSettings settings = HudSettings.getInstance();
            settings.clearPositions();
            settings.getPositions().putAll(POSITIONS_WERE);
            settings.getAnchors().putAll(ANCHORS_WERE);
            settings.replaceGroups(GROUPS_WERE);
            settings.setHudScale(scaleWas);
            settings.setHudFpsCapEnabled(capWas);
            settings.setHudFpsLimit(limitWas);
            option("BetterF3", "Hide Lads HUD", hideWas);
            ENABLED_WERE.forEach(Module::setEnabled);
            d.command("scoreboard objectives remove ladsqa");
            d.effect(null, 0);
            for (String rule : new String[] {"sendCommandFeedback", "send_command_feedback", "logAdminCommands", "log_admin_commands"}) d.command("gamerule " + rule + " true");
            d.guiScale(2);
            d.window(0, 0);
        } catch (RuntimeException failure) {
            FAILURES.add("restoring: " + failure);
        }
        if (!FAILURES.isEmpty()) LOGGER.error("Lads hud layout FAILED: {}", String.join(" | ", FAILURES));
        com.thelads.core.client.qa.QaProgress.done();
        LOGGER.info("Lads hud layout END: {} passed, {} failed", passed, FAILURES.size());
    }
}
