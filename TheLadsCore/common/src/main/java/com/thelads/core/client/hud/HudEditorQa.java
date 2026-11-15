package com.thelads.core.client.hud;

import com.google.gson.JsonObject;
import com.thelads.core.client.gui.DraggableHudScreen;
import com.thelads.core.client.hud.HudGroupLayout.Rect;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only, the same on every version (the HUD layout probe's driver runs it instead of HudLayoutQa when LADS_QA_HUDEDIT=1 or
 * -Dthelads.qaHudEdit=true): the HUD editor's mouse wheel and drags in game. For each window size (LADS_QA_HUDEDIT_SIZES, "WxH@guiScale"
 * separated by commas; the default is three) the editor opens on the default layout, every HUD shown is dragged to the top edge and
 * each corner with the pointer kept inside the window, and one of them is then resized with the wheel (125%, 75%, Control 5% steps)
 * in the middle and in the bottom-right corner, where it must stay on screen. Frames go to screenshots/hudedit-*.png; lines
 * "Lads hud layout check PASS/FAIL: hudedit ...", ending with "Lads hud layout END: N passed, M failed" (the HUD layout probe's
 * markers). LADS_QA_HUDEDIT_LEGACY=1 runs it with the clamp as it was before 1.13.20 (HudAnchors.legacyEditingBlocks).
 */
public final class HudEditorQa {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final String DEFAULT_SIZES = "1280x720@0,854x480@0,640x400@0";
    private static final List<String> SHOWN = List.of("FPS", "Coordinates", "CPS", "Day", "PingHUD");
    private static final String[] TARGETS = {"top", "topleft", "topright", "bottomleft", "bottomright"};
    private static int step = -1, wait, passed, skipped;
    private static final List<String> FAILURES = new ArrayList<>();
    private static final Deque<Runnable> QUEUE = new ArrayDeque<>();
    private static final Map<String, int[]> POSITIONS_WERE = new HashMap<>();
    private static final Map<String, HudSettings.Anchor> ANCHORS_WERE = new HashMap<>();
    private static final List<Set<String>> GROUPS_WERE = new ArrayList<>();
    private static final Map<Module, Boolean> ENABLED_WERE = new LinkedHashMap<>();
    private static final Map<SliderOption, Double> SIZES_WERE = new LinkedHashMap<>();
    private static int scaleWas;
    private static HudLayoutQa.Driver d;
    private static DraggableHudScreen ui;
    private static String tag = "";
    private static double startX, startY;
    private static boolean grabbed;
    private static Rect lastEditor;

    private HudEditorQa() {}

    /** Whether the HUD layout probe runs this sequence instead. */
    public static boolean wanted() {
        return "1".equals(System.getenv("LADS_QA_HUDEDIT")) || Boolean.getBoolean("thelads.qaHudEdit");
    }

    public static int failed() { return FAILURES.size(); }

    /** Each client tick of the probe; true once the sequence has ended. */
    public static boolean tick(HudLayoutQa.Driver driver) {
        try {
            if (wait > 0) { wait--; return false; }
            d = driver;
            if (step == -1) { begin(); step = 0; wait = 20; return false; }
            Runnable next = QUEUE.pollFirst();
            if (next == null) { end(); return true; }
            next.run();
            return false;
        } catch (RuntimeException failure) {
            FAILURES.add("run: " + failure);
            LOGGER.error("Lads hud layout check FAIL: hudedit: run", failure);
            QUEUE.clear();
            end();
            return true;
        }
    }

    private static void act(int after, Runnable action) { QUEUE.addLast(() -> { action.run(); wait = after; }); }

    private static void insert(List<Runnable> actions) { for (int i = actions.size() - 1; i >= 0; i--) QUEUE.addFirst(actions.get(i)); }

    private static void begin() {
        HudSettings settings = HudSettings.getInstance();
        POSITIONS_WERE.putAll(settings.getPositions());
        ANCHORS_WERE.putAll(settings.getAnchors());
        settings.getGroups().forEach(group -> GROUPS_WERE.add(Set.copyOf(group)));
        scaleWas = settings.getHudScale();
        HudAnchors.legacyEditingBlocks = "1".equals(System.getenv("LADS_QA_HUDEDIT_LEGACY"));
        for (HudElement element : HudManager.getInstance().getElements()) {
            Module module = element.getModuleName() == null ? null : ModuleManager.getInstance().getModule(element.getModuleName());
            if (module == null) continue;
            ENABLED_WERE.putIfAbsent(module, module.isEnabled());
            if (module.getOption("Size") instanceof SliderOption size) SIZES_WERE.putIfAbsent(size, size.getValue());
            module.setEnabled(SHOWN.contains(module.getName())); // only these, so nothing else lies over them or docks to them
        }
        Module autohide = ModuleManager.getInstance().getModule("Autohide");
        if (autohide != null) { ENABLED_WERE.putIfAbsent(autohide, autohide.isEnabled()); autohide.setEnabled(false); } // it would fade the HUD out
        settings.clearPositions();
        settings.getAnchors().clear();
        settings.replaceGroups(List.of());
        settings.setHudScale(0);
        for (SliderOption size : SIZES_WERE.keySet()) size.setValue(100);
        String sizes = System.getenv("LADS_QA_HUDEDIT_SIZES");
        for (String entry : (sizes == null || sizes.isBlank() ? DEFAULT_SIZES : sizes).split(",")) {
            String[] parts = entry.trim().split("[x@]");
            int w = Integer.parseInt(parts[0]), h = Integer.parseInt(parts[1]), gui = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
            queueSize(w, h, gui);
        }
        LOGGER.info("Lads hud layout BEGIN: hud editor sequence{}", HudAnchors.legacyEditingBlocks ? " (clamp as before 1.13.20)" : "");
    }

    private static void queueSize(int w, int h, int gui) {
        act(40, () -> { tag = w + "x" + h; d.guiScale(gui); d.window(w, h); HudSettings.getInstance().clearPositions(); HudSettings.getInstance().getAnchors().clear(); });
        act(40, () -> d.openEditor());
        act(30, HudEditorQa::plan);
    }

    /** The HUDs the editor shows now, each dragged to the edges, the first one resized, then the game. */
    private static void plan() {
        ui = d.editor();
        if (ui == null) throw new IllegalStateException("the HUD editor did not open");
        // Dynamic HUDs (Action Bar, Boss Bar) have no content in game to compare: the others, those that touch by default first.
        List<String> names = new ArrayList<>();
        for (String name : List.of("FPS", "Coordinates", "CPS", "Day", "PingHUD")) if (ui.listedNames().contains(name) && ui.boundsFor(name) != null) names.add(name);
        for (String name : ui.listedNames()) if (ui.boundsFor(name) != null && names.size() < 8 && !names.contains(name) && !name.equals("Action Bar") && !name.equals("BossBar")) names.add(name);
        Rect hud = ui.hudViewport();
        LOGGER.info("Lads hud layout hudedit {}: window {}x{} in HUD units, preview {} scale {}, HUDs {}", tag, hud.width(), hud.height(), ui.previewBounds(), ui.previewScale(), names);
        expect("editor shows HUDs at " + tag, names.size() >= 3, names.toString());
        // The first HUD gets the screenshots and the wheel.
        String lead = names.contains("FPS") ? "FPS" : names.get(0);
        names.remove(lead);
        names.add(0, lead);
        List<Runnable> actions = new ArrayList<>();
        for (String name : names) {
            for (String target : TARGETS) addDrag(actions, name, target, name.equals(lead));
            if (name.equals(lead)) addWheel(actions, name);
        }
        if (names.contains("FPS") && names.contains("Coordinates")) addStack(actions);
        actions.add(() -> { lastEditor = ui.boundsFor(lead); d.shot("hudedit-" + tag + "-editor-end"); wait = 10; });
        actions.add(() -> { d.closeScreen(); wait = 30; });
        actions.add(() -> {
            Rect shown = HudManager.getInstance().lastBounds(lead);
            expect("closing the editor keeps " + lead + " where the editor showed it (" + tag + ")", shown != null && lastEditor != null && shown.equals(lastEditor), shown + " editor " + lastEditor);
            d.shot("hudedit-" + tag + "-ingame");
            wait = 10;
        });
        insert(actions);
    }

    /**
     * A stack in the top-left corner: FPS right under Coordinates, docked to it by a drop. Coordinates, the one holding FPS, is then
     * dragged to the bottom-left corner: it must land on the bottom edge (before 1.13.20 FPS, docked under it, held it 18 px up).
     */
    private static void addStack(List<Runnable> actions) {
        actions.add(() -> {
            HudSettings settings = HudSettings.getInstance();
            settings.clearPositions();
            settings.getAnchors().clear();
            settings.setPosition("Coordinates", 0, 0);
            wait = 6;
        });
        actions.add(() -> {
            Rect c = ui.boundsFor("Coordinates");
            HudSettings.getInstance().setPosition("FPS", 0, c.bottom() + 2);
            wait = 6;
        });
        actions.add(() -> { // a drop of Coordinates a few pixels along the top: FPS touches it, so it docks under it
            Rect box = ui.boundsFor("Coordinates");
            grab("Coordinates", box);
            ui.mouseDragged(startX + 6 * ui.previewScale(), startY, 0);
            wait = 3;
        });
        actions.add(() -> { ui.mouseReleased(startX + 6 * ui.previewScale(), startY, 0, 0); wait = 6; });
        actions.add(() -> {
            HudSettings.getInstance().getAnchors().forEach((name, anchor) -> LOGGER.info("Lads hud layout hudedit {}: stack anchor {} -> {}", tag, name, anchor));
            d.shot("hudedit-" + tag + "-stack-top");
            wait = 10;
        });
        addDrag(actions, "Coordinates", "bottomleft", true);
        actions.add(() -> {
            Rect fps = ui.boundsFor("FPS"), hud = ui.hudViewport();
            expect("FPS stays on screen after its holder is dragged to the bottom (" + tag + ")", fps.y() >= 0 && fps.bottom() <= hud.height(), fps + " in " + hud);
        });
    }

    private static Rect pointer() {
        Rect screen = ui.screenViewport();
        return new Rect(0, 0, screen.width() - 1, screen.height() - 1);
    }

    private static double[] inside(Rect box) {
        Rect onScreen = ui.toScreen(box), frame = ui.previewBounds();
        return new double[] {Math.max(frame.x(), Math.min(frame.right() - 0.5, onScreen.x() + onScreen.width() / 2.0)),
            Math.max(frame.y(), Math.min(frame.bottom() - 0.5, onScreen.y() + onScreen.height() / 2.0))};
    }

    /** Selects a HUD (a click on it), as a player does before dragging or using the wheel. */
    private static void addSelect(List<Runnable> actions, String name) {
        actions.add(() -> {
            Rect box = ui.boundsFor(name);
            if (box == null) throw new IllegalStateException(name + " is not in the editor");
            grab(name, box);
            if (grabbed) ui.mouseReleased(startX, startY, 0, 0);
            wait = 3;
        });
    }

    /** Presses on the HUD: its middle, else a point near each corner (another HUD may lie over the middle). */
    private static void grab(String name, Rect box) {
        Rect onScreen = ui.toScreen(box), frame = ui.previewBounds();
        double[][] points = {inside(box), {onScreen.x() + 1.5, onScreen.y() + 1.5}, {onScreen.right() - 1.5, onScreen.bottom() - 1.5}, {onScreen.right() - 1.5, onScreen.y() + 1.5}, {onScreen.x() + 1.5, onScreen.bottom() - 1.5}};
        grabbed = false;
        for (double[] at : points) {
            if (at[0] < frame.x() || at[1] < frame.y() || at[0] >= frame.right() || at[1] >= frame.bottom()) continue;
            startX = at[0]; startY = at[1];
            if (ui.mouseClicked(startX, startY, 0, 0) && ui.selectedNames().equals(Set.of(name))) { grabbed = true; return; }
            ui.mouseReleased(startX, startY, 0, 0);
        }
    }

    private static void addDrag(List<Runnable> actions, String name, String target, boolean shot) {
        double[] end = new double[2];
        actions.add(() -> { // press on it
            Rect box = ui.boundsFor(name);
            if (box == null) throw new IllegalStateException(name + " is not in the editor");
            grab(name, box);
            Rect window = pointer();
            // Far past the target, held to the window as the pointer is.
            end[0] = target.endsWith("left") ? 0 : target.endsWith("right") ? window.right() : window.width() / 2.0;
            end[1] = target.startsWith("top") ? 0 : window.bottom();
            if (!grabbed) { skipped++; LOGGER.info("Lads hud layout hudedit {}: {} cannot be grabbed at {},{} (selected {}); {} skipped", tag, name, startX, startY, ui.selectedNames(), target); }
            wait = 2;
        });
        for (int i = 1; i <= 3; i++) {
            int part = i;
            actions.add(() -> { if (grabbed) ui.mouseDragged(startX + (end[0] - startX) * part / 3, startY + (end[1] - startY) * part / 3, 0); wait = 2; });
        }
        actions.add(() -> { ui.mouseReleased(end[0], end[1], 0, 0); wait = 4; });
        actions.add(() -> {
            if (grabbed) {
                Rect landed = ui.boundsFor(name), hud = ui.hudViewport();
                boolean x = target.endsWith("left") ? landed.x() == 0 : target.endsWith("right") ? landed.right() == hud.width() : landed.x() >= 0 && landed.right() <= hud.width();
                boolean y = target.startsWith("top") ? landed.y() == 0 : landed.bottom() == hud.height();
                int[] saved = HudSettings.getInstance().getPosition(name);
                expect(name + " dragged to " + target + " at " + tag + " lands on the edge", x && y, landed + " in " + hud + " window pointer " + (int) end[0] + "," + (int) end[1]);
                if (!name.equals("Memory")) // its text grows as the numbers do: shown wider than when it was saved
                expect(name + " at " + target + " is saved where it shows (" + tag + ")", saved != null && saved[0] == landed.x() && saved[1] == landed.y(), (saved == null ? "none" : saved[0] + "," + saved[1]) + " vs " + landed);
            }
            if (shot && grabbed) { d.shot("hudedit-" + tag + "-" + name.replace(' ', '_') + "-" + target); wait = 10; }
        });
    }

    /** The lead HUD in the bottom-right corner: wheel up and down, Control, and what is saved. */
    private static void addWheel(List<Runnable> actions, String name) {
        SliderOption size = (SliderOption) ModuleManager.getInstance().getModule(name).getOption("Size");
        addSelect(actions, name);
        for (Object[] row : new Object[][] {{1.0, false, 125.0, "wheel125"}, {1.0, false, 150.0, "wheel150"}, {-1.0, false, 125.0, null}, {-1.0, false, 100.0, null}, {-1.0, false, 75.0, "wheel75"},
            {1.0, true, 80.0, "ctrl80"}, {1.0, true, 85.0, null}, {-1.0, true, 80.0, null}, {-1.0, true, 75.0, null}}) {
            double amount = (Double) row[0];
            boolean ctrl = (Boolean) row[1];
            double want = (Double) row[2];
            String shot = (String) row[3];
            actions.add(() -> {
                Rect box = ui.boundsFor(name);
                double[] at = inside(box);
                boolean routed = !ctrl && d.scroll(at[0], at[1], amount);
                if (!routed) ui.mouseScrolled(at[0], at[1], amount, ctrl ? 2 : 0);
                wait = 4;
            });
            actions.add(() -> {
                Rect box = ui.boundsFor(name), hud = ui.hudViewport();
                expect(name + " " + (ctrl ? "Ctrl+" : "") + "wheel " + (amount > 0 ? "up" : "down") + " makes it " + (int) want + "% at " + tag, size.getValue() == want, "size " + size.getValue());
                expect(name + " at " + (int) want + "% stays on screen at " + tag, box.x() >= 0 && box.y() >= 0 && box.right() <= hud.width() && box.bottom() <= hud.height(), box + " in " + hud);
                if (shot != null) { d.shot("hudedit-" + tag + "-" + shot); wait = 10; }
            });
        }
        // Saved the way the editor saves a size: the Size option in the config file.
        actions.add(() -> wait = 30);
        actions.add(() -> {
            double saved = savedSize(name);
            expect(name + " size is saved in the config file (" + tag + ")", saved == size.getValue(), "file " + saved + " vs " + size.getValue());
        });
        // The pointer is still over the preview: a HUD in the corner grows and shrinks around the corner it holds.
        actions.add(() -> {
            Rect hud = ui.hudViewport(), box = ui.boundsFor(name);
            expect(name + " resized in the corner keeps its corner (" + tag + ")", box.right() == hud.width() && box.bottom() == hud.height(), box + " in " + hud);
        });
        actions.add(() -> { size.setValue(100); wait = 3; });
    }

    private static double savedSize(String name) {
        try {
            var file = com.thelads.core.client.util.ClientPaths.getConfigFile().toPath();
            JsonObject json = com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            return json.getAsJsonObject("modules").getAsJsonObject(name).getAsJsonObject("options").get("Size").getAsDouble();
        } catch (Exception failure) {
            LOGGER.warn("Lads hud layout hudedit: config file not read: {}", failure.toString());
            return -1;
        }
    }

    private static void expect(String what, boolean ok, String detail) {
        if (ok) { passed++; LOGGER.info("Lads hud layout check PASS: hudedit: {}", what); }
        else { FAILURES.add(what + " (" + detail + ")"); LOGGER.error("Lads hud layout check FAIL: hudedit: {} ({})", what, detail); }
    }

    private static void end() {
        step = 99;
        try {
            HudSettings settings = HudSettings.getInstance();
            settings.clearPositions();
            settings.getPositions().putAll(POSITIONS_WERE);
            settings.getAnchors().putAll(ANCHORS_WERE);
            settings.replaceGroups(GROUPS_WERE);
            settings.setHudScale(scaleWas);
            ENABLED_WERE.forEach(Module::setEnabled);
            SIZES_WERE.forEach(SliderOption::setValue);
            HudAnchors.legacyEditingBlocks = false;
            d.closeScreen();
            d.guiScale(2);
            d.window(0, 0);
        } catch (RuntimeException failure) {
            FAILURES.add("restoring: " + failure);
        }
        if (!FAILURES.isEmpty()) LOGGER.error("Lads hud layout FAILED: {}", String.join(" | ", FAILURES));
        LOGGER.info("Lads hud layout END: {} passed, {} failed ({} drags skipped: another HUD lies over the grab point)", passed, FAILURES.size(), skipped);
    }
}
