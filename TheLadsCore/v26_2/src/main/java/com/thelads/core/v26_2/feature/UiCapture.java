package com.thelads.core.v26_2.feature;

import com.google.gson.JsonElement;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.thelads.core.client.gui.DraggableHudScreen;
import com.thelads.core.client.gui.LadsSettingsScreen;
import com.thelads.core.client.hud.HudElement;
import com.thelads.core.client.hud.HudGroupLayout.Rect;
import com.thelads.core.client.hud.HudManager;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.Module;
import com.thelads.core.config.Option;
import com.thelads.core.config.SliderOption;
import com.thelads.core.v26_2.gui.DraggableHudScreen26;
import com.thelads.core.v26_2.gui.LadsSettingsScreen26;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only (auto-world, ".lads-qa-capture-ui" from the harness's LADS_VERIFY_CAPTURE_UI): the 1.7.2 menu changes through the real
 * screens and their real mouse and key handlers, screenshots in screenshots/ui172. (1) The HUD editor's Reset: the question
 * (screenshot), Cancel and Esc leave the layout, Confirm and Enter reset it. (2) A HUD module's Reset options, the same. (3) Fullbright
 * at midnight with its menu open: the Gamma slider dragged to 0, 30, 65 and 100% and a number typed, the preview's world measured two
 * frames after each change (the menu never closes), every drag position a multiple of 5, typed 67 landing on 65 and 250 refused.
 * LADS_VERIFY_UI_CONTROL=defer runs the Fullbright part with the live lightmap update off (the game as before 1.7.2): the world stays
 * as it was under the open menu and changes only once the menu closes. Everything is put back; nothing is saved but the sandbox's config.
 */
final class UiCapture {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private interface Step { void run(Minecraft mc) throws Exception; }
    private static final String CONTROL = System.getenv("LADS_VERIFY_UI_CONTROL");
    private static final List<String> FIXTURE = List.of("CPS", "Day", "FPS", "Health");
    private static final Map<Module, Boolean> ENABLED = new LinkedHashMap<>();
    private static final Map<Option, JsonElement> OPTIONS = new LinkedHashMap<>();
    private static final Map<Module, Long> MODIFIED = new LinkedHashMap<>();
    private static final Map<String, int[]> POSITIONS = new HashMap<>();
    private static final List<Set<String>> GROUPS = new ArrayList<>();
    private static final List<String> FAILURES = new ArrayList<>();
    private static final Map<String, Double> LUMA = new LinkedHashMap<>();
    private static List<Step> steps;
    private static Set<String> locked;
    private static int step = -1, passed, frames, resumeAt, saves;
    private static String pendingShot;
    private static boolean shotRunning, restored;
    private static final Map<HudElement, int[]> ELEMENTS = new LinkedHashMap<>();
    private static DraggableHudScreen controller;
    private static DraggableHudScreen26 editor;
    private static LadsSettingsScreen26 menu;
    private static Module fullbright;
    private static int timeBefore = Integer.MIN_VALUE;
    private static Map<String, int[]> layout;
    private static double lumaCaptured;
    private UiCapture() {}

    static boolean busy() { return step >= 0 && step < 999; }

    static void tick(Path game, boolean ready) {
        if (busy()) { advance(); return; }
        if (step >= 0 || !ready) return;
        Path request = game.resolve(".lads-qa-capture-ui");
        if (!Files.isRegularFile(request, LinkOption.NOFOLLOW_LINKS)) return;
        try { Files.delete(request); } catch (Exception failure) { LOGGER.error("Lads UI capture FAILED: request", failure); return; }
        Minecraft mc = Minecraft.getInstance();
        for (String name : FIXTURE) {
            Module module = NativeQualityOfLife.module(name);
            remember(module);
        }
        fullbright = NativeQualityOfLife.module("Fullbright");
        remember(fullbright);
        HudSettings.getInstance().getPositions().forEach((name, value) -> POSITIONS.put(name, value.clone()));
        HudSettings.getInstance().getGroups().forEach(group -> GROUPS.add(Set.copyOf(group)));
        locked = Set.copyOf(HudSettings.getInstance().getLocked());
        for (HudElement element : HudManager.getInstance().getElements()) ELEMENTS.put(element, new int[] {element.getX(), element.getY()});
        NativeFeatures.qaDeferGamma = "defer".equals(CONTROL);
        steps = new ArrayList<>();
        if (!"defer".equals(CONTROL)) { hudEditor(); moduleReset(); }
        fullbright();
        LOGGER.info("Lads UI capture BEGIN: {} steps{}; real screens, real mouse and key handlers; the menu stays open for every Fullbright change", steps.size(),
            "defer".equals(CONTROL) ? " (CONTROL: the lightmap is left to vanilla's tick)" : "");
        step = 0;
        resumeAt = 0;
    }

    private static void remember(Module module) {
        ENABLED.put(module, module.isEnabled());
        MODIFIED.put(module, module.getLastModified());
        for (Option option : module.getOptions()) OPTIONS.put(option, option.save().deepCopy());
    }

    /** One step per client tick, once the frames it waits for have been drawn and its screenshot is saved. */
    private static void advance() {
        if (pendingShot != null || shotRunning || frames < resumeAt) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (step >= steps.size()) { finish(mc); return; }
            steps.get(step++).run(mc);
        } catch (Throwable failure) {
            fail("step " + step + ": " + failure);
            LOGGER.error("Lads UI capture step {}", step, failure);
            finish(mc);
        }
    }

    // ---- 1. The HUD editor's Reset ----
    private static void hudEditor() {
        steps.add(mc -> {
            HudSettings settings = HudSettings.getInstance();
            for (String name : FIXTURE) NativeQualityOfLife.module(name).setEnabled(true);
            settings.getPositions().clear();
            settings.replaceGroups(List.of());
            settings.replaceLocked(Set.of());
            settings.setPosition("CPS", 12, 42);
            settings.setPosition("Day", 12, 78);
            settings.setPosition("Health", 12, 112);
            settings.setPosition("FPS", 200, 20);
            controller = new DraggableHudScreen(() -> saves++);
            editor = new DraggableHudScreen26(null, controller);
            mc.setScreenAndShow(editor);
            pause(30);
        });
        steps.add(mc -> {
            check(controller.boundsFor("CPS") != null, "the HUD editor shows the fixture layout");
            layout = snapshot(); // as the editor drew it
            shot("ui-hud-1-before");
        });
        steps.add(mc -> { pressControl(controller, "reset"); pause(4); });
        steps.add(mc -> {
            check(controller.confirmDialog().isOpen(), "Reset opens the question");
            check(snapshot().equals(layout) && saves == 0, "asking changed nothing");
            shot("ui-hud-2-dialog");
        });
        steps.add(mc -> { click(editor, controller.confirmDialog().cancelBounds()); pause(4); });
        steps.add(mc -> {
            check(!controller.confirmDialog().isOpen() && mc.gui.screen() == editor, "Cancel closes the question and keeps the editor");
            check(snapshot().equals(layout) && saves == 0, "Cancel leaves the layout untouched");
            shot("ui-hud-3-cancelled");
        });
        steps.add(mc -> { pressControl(controller, "reset"); pause(2); });
        steps.add(mc -> { key(editor, 256); pause(2); });
        steps.add(mc -> {
            check(!controller.confirmDialog().isOpen() && mc.gui.screen() == editor, "Esc cancels the question and does not leave the editor");
            check(snapshot().equals(layout), "Esc leaves the layout untouched");
            pressControl(controller, "reset");
            pause(2);
        });
        steps.add(mc -> { click(editor, controller.confirmDialog().confirmBounds()); pause(30); });
        steps.add(mc -> {
            check(!controller.confirmDialog().isOpen() && HudSettings.getInstance().getPositions().isEmpty() && saves == 1, "Confirm resets every HUD position");
            shot("ui-hud-4-confirmed");
        });
        steps.add(mc -> {
            HudSettings.getInstance().setPosition("CPS", 12, 42);
            layout = snapshot();
            pause(3);
        });
        steps.add(mc -> { pressControl(controller, "reset"); pause(2); });
        steps.add(mc -> { key(editor, 257); pause(3); });
        steps.add(mc -> {
            check(HudSettings.getInstance().getPositions().isEmpty() && saves == 2, "Enter confirms: the layout is reset");
            mc.setScreenAndShow(null);
            pause(5);
        });
    }

    // ---- 2. A HUD module's Reset options ----
    private static void moduleReset() {
        steps.add(mc -> {
            ((SliderOption) NativeQualityOfLife.module("CPS").getOption("Size")).setValue(150);
            menu = new LadsSettingsScreen26(null);
            mc.setScreenAndShow(menu);
            menu.openModule("CPS");
            pause(20);
        });
        steps.add(mc -> {
            check(size() == 150 && ui().controlBounds("reset") != null, "the CPS page shows Size 150 and its Reset options button");
            shot("ui-module-1-before");
        });
        steps.add(mc -> { click(menu, ui().controlBounds("reset")); pause(4); });
        steps.add(mc -> {
            check(ui().confirmDialog().isOpen() && size() == 150, "Reset options opens the question, Size still 150");
            shot("ui-module-2-dialog");
        });
        steps.add(mc -> { click(menu, ui().confirmDialog().cancelBounds()); pause(4); });
        steps.add(mc -> {
            check(!ui().confirmDialog().isOpen() && size() == 150 && mc.gui.screen() == menu, "Cancel keeps the options and the page");
            shot("ui-module-3-cancelled");
        });
        steps.add(mc -> { click(menu, ui().controlBounds("reset")); pause(2); });
        steps.add(mc -> { key(menu, 256); pause(3); });
        steps.add(mc -> {
            check(!ui().confirmDialog().isOpen() && size() == 150 && mc.gui.screen() == menu, "Esc cancels the question, not the page; Size is still 150");
            click(menu, ui().controlBounds("reset"));
            pause(2);
        });
        steps.add(mc -> { click(menu, ui().confirmDialog().confirmBounds()); pause(10); });
        steps.add(mc -> {
            check(!ui().confirmDialog().isOpen() && size() == 100, "Confirm resets the module's options (Size back to 100)");
            shot("ui-module-4-confirmed");
        });
        steps.add(mc -> { ((SliderOption) NativeQualityOfLife.module("CPS").getOption("Size")).setValue(150); pause(3); });
        steps.add(mc -> { click(menu, ui().controlBounds("reset")); pause(2); });
        steps.add(mc -> { key(menu, 257); pause(3); });
        steps.add(mc -> {
            check(size() == 100, "Enter confirms: Size back to 100");
            mc.setScreenAndShow(null);
            pause(5);
        });
    }

    // ---- 3. Fullbright, menu open ----
    private static void fullbright() {
        steps.add(mc -> {
            var server = mc.getSingleplayerServer();
            check(server != null, "an integrated server to set the clock");
            server.execute(() -> {
                try {
                    var source = server.createCommandSourceStack().withSuppressedOutput();
                    var commands = server.getCommands().getDispatcher();
                    timeBefore = commands.execute("time query time", source);
                    commands.execute("time set midnight", source);
                } catch (Exception failure) { fail("clock: " + failure); }
            });
            fullbright.setEnabled(true);
            gamma().setValue(0);
            menu = new LadsSettingsScreen26(null);
            mc.setScreenAndShow(menu);
            menu.openModule("Fullbright");
            pause(90);
        });
        steps.add(mc -> {
            long time = Math.floorMod(mc.level.getDefaultClockTime(), 24000L);
            check(Math.abs(time - 18000) <= 1500, "the QA world is at night (clock " + time + ")");
            check(ui().gameViewBounds() != null, "the Fullbright page draws the live world in its preview");
            check(mc.isPaused(), "the game is paused under the menu, so vanilla's lightmap tick does not run");
            shot("ui-fb-p000");
        });
        for (int percent : new int[] {30, 65, 100, 0}) {
            steps.add(mc -> { drag(percent); pause(2); });
            steps.add(mc -> {
                check(gamma().getValue() == percent && mc.gui.screen() == menu, "the slider dragged to " + percent + "% stands at " + gamma().getValue() + " and the menu is still open");
                shot(String.format(Locale.ROOT, "ui-fb-p%03d", percent) + (percent == 0 ? "-again" : ""));
            });
        }
        steps.add(mc -> { stepsDrag(); pause(2); });
        steps.add(mc -> { check(dragStepsOk, "every position of a drag from 0 to 100 was a multiple of 5"); typed("40"); pause(2); });
        steps.add(mc -> { check(gamma().getValue() == 40, "40 typed in the field (Enter) is 40%"); shot("ui-fb-typed-040"); });
        steps.add(mc -> { typed("67"); pause(2); });
        steps.add(mc -> { check(gamma().getValue() == 65, "67 typed lands on the nearest step, 65%"); typed("250"); pause(2); });
        steps.add(mc -> { check(gamma().getValue() == 65, "250 typed (out of range) is refused: the value stays 65%"); typed("abc"); pause(2); });
        steps.add(mc -> {
            check(gamma().getValue() == 65 && !ui().isEditingText(), "letters typed are ignored and the value stays 65%");
            pause(2);
        });
        if (CONTROL == null) { steps.add(mc -> evaluate()); return; }
        // Control: the world under the open menu stayed as it was; the menu closes and only then does the 65% show.
        steps.add(mc -> {
            check(Math.abs(LUMA.get("ui-fb-p100") - LUMA.get("ui-fb-p000")) < 2, "CONTROL: the world under the open menu did not change at any setting");
            mc.setScreenAndShow(null);
            pause(40);
        });
        steps.add(mc -> shot("ui-fb-menu-closed"));
        steps.add(mc -> {
            check(lumaCaptured > LUMA.get("ui-fb-p000") + 15, "CONTROL: after the menu closed the world is brighter: " + String.format(Locale.ROOT, "%.1f", lumaCaptured));
        });
    }

    private static boolean dragStepsOk;

    private static void evaluate() {
        double l0 = LUMA.get("ui-fb-p000"), l30 = LUMA.get("ui-fb-p030"), l65 = LUMA.get("ui-fb-p065"), l100 = LUMA.get("ui-fb-p100");
        LOGGER.info("Lads UI capture world brightness (mean of the preview, 0-255) 0%: {} 30%: {} 65%: {} 100%: {}",
            String.format(Locale.ROOT, "%.1f", l0), String.format(Locale.ROOT, "%.1f", l30), String.format(Locale.ROOT, "%.1f", l65), String.format(Locale.ROOT, "%.1f", l100));
        check(l30 > l0 + 3 && l65 >= l30 && l100 >= l65 && l100 > l0 + 15, "the world under the open menu follows the slider: brighter at each higher setting");
        check(Math.abs(LUMA.get("ui-fb-p000-again") - l0) < 2, "back at 0% the world is as dark as at the start");
    }

    private static SliderOption gamma() { return (SliderOption) fullbright.getOption("Gamma"); }
    private static LadsSettingsScreen ui() { return menu.ui(); }
    private static int size() { return (int) ((SliderOption) NativeQualityOfLife.module("CPS").getOption("Size")).getValue(); }
    private static Map<String, int[]> snapshot() {
        Map<String, int[]> now = new HashMap<>();
        HudSettings.getInstance().getPositions().forEach((name, value) -> now.put(name, value.clone()));
        return new SnapshotMap(now);
    }

    /** A position map that compares by content. */
    private static final class SnapshotMap extends HashMap<String, int[]> {
        SnapshotMap(Map<String, int[]> source) { super(source); }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Map<?, ?> map) || map.size() != size()) return false;
            for (var entry : entrySet()) if (!(map.get(entry.getKey()) instanceof int[] value) || !java.util.Arrays.equals(value, entry.getValue())) return false;
            return true;
        }
        @Override public int hashCode() { return size(); }
    }

    /** Presses the slider at the point of this percentage and releases there: a click-and-drag with a real press, move and release. */
    private static void drag(int percent) {
        Rect track = bounds(ui().controlBounds("option:Gamma"));
        double y = track.y() + track.height() / 2.0, from = track.x() + track.width() / 2.0;
        double to = track.x() + 4 + (track.width() - 8) * percent / 100.0;
        menu.mouseClicked(mouse(from, y), false);
        for (int i = 1; i <= 6; i++) menu.mouseDragged(mouse(from + (to - from) * i / 6, y), (to - from) / 6, 0);
        menu.mouseReleased(mouse(to, y));
    }

    /** A drag across the whole track: every value the slider takes on the way. */
    private static void stepsDrag() {
        Rect track = bounds(ui().controlBounds("option:Gamma"));
        double y = track.y() + track.height() / 2.0;
        menu.mouseClicked(mouse(track.x() + 4, y), false);
        boolean ok = true;
        List<String> values = new ArrayList<>();
        for (double x = track.x() + 4; x <= track.x() + track.width() - 4; x += 1.5) {
            menu.mouseDragged(mouse(x, y), 1.5, 0);
            double v = gamma().getValue();
            values.add(gamma().display());
            ok &= v % 5 == 0;
        }
        menu.mouseReleased(mouse(track.x() + track.width() - 4, y));
        dragStepsOk = ok;
        LOGGER.info("Lads UI capture slider positions along one drag: {}", String.join(" ", new java.util.LinkedHashSet<>(values)));
    }

    /** Clicks the value field, types the text one character at a time and presses Enter. */
    private static void typed(String text) {
        click(menu, ui().controlBounds("option:Gamma:field"));
        text.codePoints().forEach(codepoint -> menu.charTyped(new CharacterEvent(codepoint)));
        key(menu, 257);
    }

    private static void pressControl(DraggableHudScreen hud, String id) {
        var control = hud.controls().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
        check(control.enabled(), "button enabled: " + id);
        click(editor, control.bounds());
    }
    private static Rect bounds(LadsSettingsScreen.Rect r) { return new Rect(r.x(), r.y(), r.width(), r.height()); }
    private static void click(Screen screen, LadsSettingsScreen.Rect r) { click(screen, bounds(r)); }
    private static void click(Screen screen, Rect r) {
        var event = mouse(r.x() + r.width() / 2.0, r.y() + r.height() / 2.0);
        screen.mouseClicked(event, false);
        screen.mouseReleased(event);
    }
    private static MouseButtonEvent mouse(double x, double y) { return new MouseButtonEvent(x, y, new MouseButtonInfo(0, 0)); }
    private static void key(Screen screen, int code) { screen.keyPressed(new KeyEvent(code, 0, 0)); }

    private static void pause(int frameCount) { resumeAt = frames + frameCount; }
    private static void shot(String name) { pendingShot = name; }

    /** Each completed game frame (NativeWorldVerification.renderedFrame). */
    static void frame(RenderTarget target, Path game) {
        if (!busy()) return;
        frames++;
        if (pendingShot == null || shotRunning || frames < resumeAt) return;
        String name = pendingShot;
        shotRunning = true;
        try {
            Path output = game.resolve("screenshots").resolve("ui172").resolve(name + ".png");
            Files.createDirectories(output.getParent());
            boolean preview = menu != null && Minecraft.getInstance().gui.screen() == menu && ui().gameViewBounds() != null;
            Rect view = preview ? bounds(ui().gameViewBounds()) : null;
            boolean world = Minecraft.getInstance().gui.screen() == null; // the menu closed: the whole game view
            net.minecraft.client.Screenshot.takeScreenshot(target, image -> {
                try {
                    double luma = view != null ? brightness(image, view) : world ? brightness(image, new Rect(0, 0, image.getWidth(), image.getHeight() * 85 / 100), 1) : Double.NaN;
                    if (!Double.isNaN(luma)) { LUMA.put(name, luma); lumaCaptured = luma; }
                    image.writeToFile(output);
                    LOGGER.info("Lads UI frame {}{}", output, Double.isNaN(luma) ? "" : " (world brightness " + String.format(Locale.ROOT, "%.1f", luma) + ")");
                } catch (Exception failure) { fail(name + ": " + failure); }
                finally { image.close(); pendingShot = null; shotRunning = false; }
            });
        } catch (Exception failure) {
            fail(name + ": " + failure);
            pendingShot = null;
            shotRunning = false;
        }
    }

    /** Mean of the red, green and blue values (0-255) over the preview's world, its frame left out. */
    private static double brightness(NativeImage image, Rect gui) {
        return brightness(image, gui, image.getWidth() / (double) Minecraft.getInstance().getWindow().getGuiScaledWidth());
    }
    private static double brightness(NativeImage image, Rect gui, double scale) {
        int x0 = (int) (gui.x() * scale) + 3, y0 = (int) (gui.y() * scale) + 3, x1 = (int) ((gui.x() + gui.width()) * scale) - 3, y1 = (int) ((gui.y() + gui.height()) * scale) - 3;
        long sum = 0, count = 0;
        for (int y = Math.max(0, y0); y < Math.min(image.getHeight(), y1); y += 2)
            for (int x = Math.max(0, x0); x < Math.min(image.getWidth(), x1); x += 2) {
                int c = image.getPixel(x, y);
                sum += (c & 0xFF) + (c >> 8 & 0xFF) + (c >> 16 & 0xFF);
                count += 3;
            }
        return count == 0 ? 0 : (double) sum / count;
    }

    private static void finish(Minecraft mc) {
        if (!restored) restore(mc);
        step = 999;
        if (FAILURES.isEmpty()) LOGGER.info("Lads UI capture END: {} passed, 0 failed; {}", passed, CONTROL == null ? "reset questions answered by click and key, Fullbright followed through the open menu"
            : "control run: lightmap left to vanilla's tick");
        else LOGGER.error("Lads UI capture END: {} passed, {} failed: {}", passed, FAILURES.size(), FAILURES);
    }

    private static void restore(Minecraft mc) {
        restored = true;
        NativeFeatures.qaDeferGamma = false;
        if (mc.gui.screen() == editor || mc.gui.screen() == menu) mc.setScreenAndShow(null);
        if (controller != null) controller.close();
        OPTIONS.forEach(Option::load);
        ENABLED.forEach(Module::setEnabled);
        MODIFIED.forEach(Module::setLastModified);
        HudSettings settings = HudSettings.getInstance();
        settings.getPositions().clear();
        POSITIONS.forEach((name, value) -> settings.setPosition(name, value[0], value[1]));
        settings.replaceGroups(GROUPS);
        settings.replaceLocked(locked);
        ELEMENTS.forEach((element, value) -> { element.endPositionEdit(); element.setPosition(value[0], value[1]); element.restoreSavedPosition(); });
        var server = mc.getSingleplayerServer();
        int time = timeBefore;
        if (server != null && time != Integer.MIN_VALUE) server.execute(() -> {
            try { server.getCommands().getDispatcher().execute("time set " + time, server.createCommandSourceStack().withSuppressedOutput()); }
            catch (Exception failure) { LOGGER.error("Lads UI capture: the clock could not be put back (time {})", time, failure); }
        });
        ConfigManager.save(); // the sandbox's config goes back to what it was
    }

    private static void check(boolean result, String description) {
        if (result) { passed++; LOGGER.info("Lads UI capture PASS: {}", description); }
        else fail(description);
    }
    private static void fail(String failure) {
        FAILURES.add(failure);
        LOGGER.error("Lads UI capture check failed: {}", failure);
    }
}
