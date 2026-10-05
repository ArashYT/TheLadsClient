package com.thelads.core.v26_2.feature;

import com.google.gson.JsonElement;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.thelads.core.client.bridge.LadsGraphics;
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
import java.lang.reflect.Field;
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
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only (auto-world, ".lads-qa-capture-ui" from the harness's LADS_VERIFY_CAPTURE_UI): the 1.7.2 menu changes through the real
 * screens and their real mouse and key handlers (called in-process: no OS input), screenshots in screenshots/ui172. (1) The HUD
 * editor's Reset: the question, Cancel and Esc leave the layout, Confirm and Enter reset it. (2) A HUD module's Reset options, the same.
 * (3) Fullbright at midnight with its menu open, twice: first with the live lightmap update off (a control: the game as before 1.7.2),
 * then as shipped. The Gamma slider is dragged to 30, 65, 100 and 0%, numbers are typed, and after each change the world is measured
 * (the menu never closes): the mean of the preview's pixels when frames render, and always the brightness the lightmap extractor
 * computes when asked (the same call the frame makes). Every drag position must be a multiple of 5, 67 typed lands on 65, 250 is refused.
 * A minimized 26.x window draws no frames: the screens are then laid out headlessly (the same common UI code) and no screenshot is
 * taken; the log says so. Everything is put back; the sandbox's config is saved as it was.
 */
final class UiCapture {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private interface Step { void run(Minecraft mc) throws Exception; }
    private static final List<String> FIXTURE = List.of("CPS", "Day", "FPS", "Health");
    private static final Map<Module, Boolean> ENABLED = new LinkedHashMap<>();
    private static final Map<Module, Long> MODIFIED = new LinkedHashMap<>();
    private static final Map<Option, JsonElement> OPTIONS = new LinkedHashMap<>();
    private static final Map<String, int[]> POSITIONS = new HashMap<>();
    private static final Map<HudElement, int[]> ELEMENTS = new LinkedHashMap<>();
    private static final List<Set<String>> GROUPS = new ArrayList<>();
    private static final List<String> FAILURES = new ArrayList<>();
    private static final Map<String, Double> WORLD = new LinkedHashMap<>();
    private static List<Step> steps;
    private static Set<String> locked;
    private static int step = -1, passed, frames, ticks, resumeFrames, resumeTicks, saves, skippedShots, takenShots;
    private static int framesAtLastTick;
    private static boolean rendering, shotRunning, restored, dragStepsOk;
    private static String pendingShot;
    private static int shotDeadline;
    private static DraggableHudScreen controller;
    private static DraggableHudScreen26 editor;
    private static LadsSettingsScreen26 menu;
    private static Module fullbright;
    private static int timeBefore = Integer.MIN_VALUE;
    private static Map<String, int[]> layout;
    private static Field extractorField;
    private static final Headless HEADLESS = new Headless();
    private UiCapture() {}

    static boolean busy() { return step >= 0 && step < 999; }

    static void tick(Path game, boolean ready) {
        if (busy()) { advance(); return; }
        if (step >= 0 || !ready) return;
        Path request = game.resolve(".lads-qa-capture-ui");
        if (!Files.isRegularFile(request, LinkOption.NOFOLLOW_LINKS)) return;
        try { Files.delete(request); } catch (Exception failure) { LOGGER.error("Lads UI capture FAILED: request", failure); return; }
        for (String name : FIXTURE) remember(NativeQualityOfLife.module(name));
        fullbright = NativeQualityOfLife.module("Fullbright");
        remember(fullbright);
        HudSettings.getInstance().getPositions().forEach((name, value) -> POSITIONS.put(name, value.clone()));
        HudSettings.getInstance().getGroups().forEach(group -> GROUPS.add(Set.copyOf(group)));
        locked = Set.copyOf(HudSettings.getInstance().getLocked());
        for (HudElement element : HudManager.getInstance().getElements()) ELEMENTS.put(element, new int[] {element.getX(), element.getY()});
        steps = new ArrayList<>();
        hudEditor();
        moduleReset();
        fullbright(true);
        fullbright(false);
        LOGGER.info("Lads UI capture BEGIN: {} steps; real screens and their mouse and key handlers, called in-process", steps.size());
        step = 0;
    }

    private static void remember(Module module) {
        ENABLED.put(module, module.isEnabled());
        MODIFIED.put(module, module.getLastModified());
        for (Option option : module.getOptions()) OPTIONS.put(option, option.save().deepCopy());
    }

    /** One step per client tick, once the frames (or, with no frames drawn, the ticks) it waits for have passed and its screenshot is settled. */
    private static void advance() {
        ticks++;
        rendering = frames > framesAtLastTick;
        framesAtLastTick = frames;
        Minecraft mc = Minecraft.getInstance();
        if (!rendering) layout(mc); // no frame draws the screen: lay it out here, so its controls have bounds
        if (pendingShot != null && !shotRunning && ticks >= shotDeadline) {
            LOGGER.info("Lads UI frame {} skipped: no frame was drawn (the QA window is minimized)", pendingShot);
            skippedShots++;
            pendingShot = null;
        }
        if (pendingShot != null || shotRunning || (frames < resumeFrames && ticks < resumeTicks)) return;
        try {
            if (step >= steps.size()) { finish(mc); return; }
            steps.get(step++).run(mc);
            if (!rendering) layout(mc);
        } catch (Throwable failure) {
            fail("step " + step + ": " + failure);
            LOGGER.error("Lads UI capture step {}", step, failure);
            finish(mc);
        }
    }

    /** What a frame would do for the open Lads screen: one layout pass of the common UI against a graphics that draws nothing. */
    private static void layout(Minecraft mc) {
        Screen screen = mc.gui.screen();
        if (screen == null) return;
        if (screen == editor) controller.render(HEADLESS, -1, -1);
        else if (screen == menu) ui().render(HEADLESS, -1, -1);
    }

    private static final class Headless implements LadsGraphics {
        @Override public void fill(int minX, int minY, int maxX, int maxY, int color) { }
        @Override public void drawText(String text, int x, int y, int color, boolean shadow) { }
        @Override public void drawCenteredText(String text, int centerX, int y, int color, boolean shadow) { }
        @Override public int textWidth(String text) { return Minecraft.getInstance().font.width(text); }
        @Override public int fontHeight() { return 9; }
        @Override public void pushPose() { }
        @Override public void popPose() { }
        @Override public void translate(float x, float y) { }
        @Override public void scale(float sx, float sy) { }
        @Override public void enableScissor(int minX, int minY, int maxX, int maxY) { }
        @Override public void disableScissor() { }
        @Override public void blit(String texture, int x, int y, int u, int v, int width, int height) { }
        @Override public void drawHead(String username, String uuid, int x, int y, int size) { }
        @Override public int getScaledWidth() { return Minecraft.getInstance().getWindow().getGuiScaledWidth(); }
        @Override public int getScaledHeight() { return Minecraft.getInstance().getWindow().getGuiScaledHeight(); }
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
        steps.add(mc -> { scrollDown(); pause(15); }); // Reset options sits below the last option
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
    /** {@code control}: the lightmap update for Fullbright changes is off (the game before 1.7.2); the world must stay as it was under the open menu. */
    private static void fullbright(boolean control) {
        String tag = control ? "ui-fb-control-" : "ui-fb-";
        if (control) steps.add(mc -> {
            // midnight first, with no menu open (a paused world would not send the new time)
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
            pause(90);
        });
        steps.add(mc -> {
            NativeFeatures.qaDeferGamma = control;
            fullbright.setEnabled(true);
            gamma().setValue(0);
            menu = new LadsSettingsScreen26(null);
            mc.setScreenAndShow(menu);
            menu.openModule("Fullbright");
            pause(20);
        });
        steps.add(mc -> {
            if (control) {
                long time = Math.floorMod(mc.level.getDefaultClockTime(), 24000L);
                check(Math.abs(time - 18000) <= 1500, "the QA world is at night (clock " + time + ")");
            }
            check(mc.isPaused(), "the game is paused under the menu, so vanilla's lightmap tick does not run");
            if (rendering) check(ui().gameViewBounds() != null, "the Fullbright page draws the live world in its preview");
            measure(mc, tag + "p000", 0);
        });
        for (int percent : new int[] {30, 65, 100, 0}) {
            steps.add(mc -> { drag(percent); pause(2); });
            steps.add(mc -> {
                check(gamma().getValue() == percent && mc.gui.screen() == menu, "the slider dragged to " + percent + "% stands at " + gamma().getValue() + " and the menu is still open");
                measure(mc, tag + String.format(Locale.ROOT, "p%03d", percent) + (percent == 0 ? "-again" : ""), percent);
            });
        }
        steps.add(mc -> { stepsDrag(); pause(2); });
        steps.add(mc -> { check(dragStepsOk, "every position of a drag from 0 to 100 was a multiple of 5"); typed("40"); pause(2); });
        steps.add(mc -> { check(gamma().getValue() == 40, "40 typed in the field (Enter) is 40%"); measure(mc, tag + "typed-040", 40); });
        steps.add(mc -> { typed("67"); pause(2); });
        steps.add(mc -> { check(gamma().getValue() == 65, "67 typed lands on the nearest step, 65%"); typed("250"); pause(2); });
        steps.add(mc -> { check(gamma().getValue() == 65, "250 typed (out of range) is refused: the value stays 65%"); typed("abc"); pause(2); });
        steps.add(mc -> {
            check(gamma().getValue() == 65 && !ui().isEditingText(), "letters typed are ignored and the value stays 65%");
            measure(mc, tag + "typed-065", 65);
        });
        steps.add(mc -> {
            evaluate(tag, control);
            if (control) {
                // the menu closes: the game runs again, vanilla's tick rebuilds the lightmap and only now does the 65% show
                mc.setScreenAndShow(null);
                pause(40);
            } else pause(2);
        });
        if (control) steps.add(mc -> {
            double after = sample(mc);
            WORLD.put(tag + "closed", after);
            // informational: BadOptimizations (in the pack) skips vanilla's lightmap tick until a vanilla input changes, so the 65% may stay unseen even now
            LOGGER.info("Lads UI capture CONTROL after the menu closed and the game ran again: lightmap brightness {} (the slider's 65% is gamma {})", f(after), f(effective(65)));
            NativeFeatures.qaDeferGamma = false;
            pause(2);
        });
    }

    private static void evaluate(String tag, boolean control) {
        double l0 = WORLD.get(tag + "p000"), l30 = WORLD.get(tag + "p030"), l65 = WORLD.get(tag + "p065"), l100 = WORLD.get(tag + "p100");
        LOGGER.info("Lads UI capture {} world under the open menu at 0%: {} 30%: {} 65%: {} 100%: {} (lightmap brightness / preview pixels)",
            control ? "CONTROL" : "LIVE", f(l0), f(l30), f(l65), f(l100));
        if (control) {
            check(l30 == l0 && l65 == l0 && l100 == l0, "CONTROL: without the fix the world under the open menu stays as it was at every setting");
            return;
        }
        double eps = usePixels() ? 3 : 0.01;
        check(l30 > l0 + eps && l65 >= l30 && l100 >= l65 && l100 > l0 + 5 * eps, "the world under the open menu follows the slider: brighter at each higher setting");
        check(Math.abs(WORLD.get(tag + "p000-again") - l0) < (usePixels() ? 2 : 0.001), "back at 0% the world is as dark as at the start");
    }

    private static boolean usePixels() { return takenShots > 0 && skippedShots == 0; }

    /** The gamma Fullbright gives the lightmap at this percentage (1 at 0%, 15 at 100%). */
    private static double effective(int percent) { return 1 + 14 * percent / 100.0; }

    /** The world's brightness now: the preview's mean pixel value when frames are drawn, else the lightmap's brightness from the extractor. */
    private static void measure(Minecraft mc, String name, int percent) throws Exception {
        if (rendering && ui().gameViewBounds() != null) { shot(name); return; }
        double value = sample(mc);
        WORLD.put(name, value);
        LOGGER.info("Lads UI world {}: lightmap brightness {} (the Gamma slider at {}%, gamma {})", name, f(value), percent, f(effective(percent)));
    }

    /**
     * The brightness the lightmap extractor computes when the game asks it, which is what the next frame would draw with: a frame
     * extracts the lightmap, and vanilla's own tick (which sets its needs-update flag) does not run under a menu in a paused world.
     */
    private static double sample(Minecraft mc) throws Exception {
        if (extractorField == null) {
            extractorField = net.minecraft.client.renderer.GameRenderer.class.getDeclaredField("lightmapRenderStateExtractor");
            extractorField.setAccessible(true);
        }
        var state = mc.gameRenderer.gameRenderState().lightmapRenderState;
        ((LightmapRenderStateExtractor) extractorField.get(mc.gameRenderer)).extract(state, 1f);
        return state.brightness;
    }

    /** The mouse wheel over the page, to its end. */
    private static void scrollDown() {
        ui().setReducedMotion(true);
        menu.mouseScrolled(300, 200, 0, -30);
    }

    private static SliderOption gamma() { return (SliderOption) fullbright.getOption("Gamma"); }
    private static LadsSettingsScreen ui() { return menu.ui(); }
    private static int size() { return (int) ((SliderOption) NativeQualityOfLife.module("CPS").getOption("Size")).getValue(); }
    private static String f(double value) { return String.format(Locale.ROOT, "%.2f", value); }
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

    /** Presses the slider in its middle, drags it to this percentage in steps and releases: a real press, moves and release. */
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
            values.add(gamma().display());
            ok &= gamma().getValue() % 5 == 0;
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
    private static MouseButtonEvent mouse(double x, double y) { return new MouseButtonEvent(x, y, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)); }
    /** The menus' GLFW key codes, as SDL key events carry them in 26.3. */
    private static void key(Screen screen, int code) { screen.keyPressed(new KeyEvent(code == 256 ? InputConstants.KEY_ESCAPE : InputConstants.KEY_RETURN, 0, 0)); }

    private static void pause(int frameCount) { resumeFrames = frames + frameCount; resumeTicks = ticks + frameCount / 3 + 2; }
    private static void shot(String name) {
        if (!rendering) { LOGGER.info("Lads UI frame {} skipped: no frame is drawn (the QA window is minimized)", name); skippedShots++; return; }
        pendingShot = name;
        shotDeadline = ticks + 20;
    }

    /** Each completed game frame (NativeWorldVerification.renderedFrame): none while the window is minimized. */
    static void frame(RenderTarget target, Path game) {
        if (!busy()) return;
        frames++;
        if (pendingShot == null || shotRunning || frames < resumeFrames) return;
        String name = pendingShot;
        shotRunning = true;
        try {
            Path output = game.resolve("screenshots").resolve("ui172").resolve(name + ".png");
            Files.createDirectories(output.getParent());
            boolean preview = menu != null && Minecraft.getInstance().gui.screen() == menu && ui().gameViewBounds() != null;
            Rect view = preview ? bounds(ui().gameViewBounds()) : null;
            net.minecraft.client.Screenshot.takeScreenshot(target, image -> {
                try {
                    double world = view != null ? brightness(image, view) : Double.NaN;
                    if (!Double.isNaN(world)) WORLD.put(name, world);
                    image.writeToFile(output);
                    takenShots++;
                    LOGGER.info("Lads UI frame {}{}", output, Double.isNaN(world) ? "" : " (world brightness in the preview " + f(world) + ")");
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
        double scale = image.getWidth() / (double) Minecraft.getInstance().getWindow().getGuiScaledWidth();
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
        String how = "reset questions answered by click, Esc and Enter; Fullbright followed through the open menu (control and live); " + takenShots + " frames saved, "
            + skippedShots + " skipped (minimized window draws none)";
        if (FAILURES.isEmpty()) LOGGER.info("Lads UI capture END: {} passed, 0 failed; {}", passed, how);
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
