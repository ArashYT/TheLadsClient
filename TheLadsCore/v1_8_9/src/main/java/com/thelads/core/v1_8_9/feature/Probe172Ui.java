package com.thelads.core.v1_8_9.feature;

import static com.thelads.core.v1_8_9.feature.CoreProbe.after;
import static com.thelads.core.v1_8_9.feature.CoreProbe.check;
import static com.thelads.core.v1_8_9.feature.CoreProbe.click;
import static com.thelads.core.v1_8_9.feature.CoreProbe.mouse;
import static com.thelads.core.v1_8_9.feature.CoreProbe.retry;
import static com.thelads.core.v1_8_9.feature.CoreProbe.screenshot;
import static com.thelads.core.v1_8_9.feature.CoreProbe.tap;

import com.google.gson.JsonElement;
import com.thelads.core.client.gui.DraggableHudScreen;
import com.thelads.core.client.gui.LadsSettingsScreen;
import com.thelads.core.client.hud.HudElement;
import com.thelads.core.client.hud.HudGroupLayout;
import com.thelads.core.client.hud.HudManager;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.Module;
import com.thelads.core.config.Option;
import com.thelads.core.config.SliderOption;
import com.thelads.core.v1_8_9.gui.DraggableHudScreen189;
import com.thelads.core.v1_8_9.gui.LadsSettingsScreen189;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.world.WorldServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;

/**
 * QA only (LADS_VERIFY_189_ONLY=ui): the 1.7.2 menu changes through 1.8.9's real input path (events queued in LWJGL's own mouse and
 * keyboard buffers, read by GuiScreen.handleInput), screenshots lads-qa/screenshots/ui172-*. (1) The HUD editor's Reset: the question,
 * Cancel and Esc leave the layout, Confirm and Enter reset it. (2) A HUD module's Reset options, the same. (3) Fullbright at midnight
 * with its menu open: the Gamma slider dragged to 0, 30, 65 and 100% and numbers typed, the preview's world measured after each change
 * (the menu never closes), every position of a drag a multiple of 5, 67 typed landing on 65 and 250 refused. LADS_VERIFY_UI_CONTROL=defer
 * runs the Fullbright part without telling the lightmap it is out of date (as before 1.7.2 a menu kept it): the world under the open
 * menu stays as it was and changes only once the menu closes. Everything is put back.
 */
final class Probe172Ui {
    private static final Logger LOG = LogManager.getLogger("TheLadsCore");
    private static final boolean DEFER = "defer".equals(System.getenv("LADS_VERIFY_UI_CONTROL"));
    private static final List<String> FIXTURE = Arrays.asList("CPS", "Day", "FPS", "Health");
    static final List<CoreProbe.Step> STEPS = new ArrayList<CoreProbe.Step>();
    private static final Map<Module, Boolean> enabledWere = new LinkedHashMap<Module, Boolean>();
    private static final Map<Module, Long> modifiedWere = new LinkedHashMap<Module, Long>();
    private static final Map<Option, JsonElement> optionsWere = new LinkedHashMap<Option, JsonElement>();
    private static final Map<String, int[]> positionsWere = new HashMap<String, int[]>();
    private static final List<Set<String>> groupsWere = new ArrayList<Set<String>>();
    private static final Map<HudElement, int[]> elementsWere = new LinkedHashMap<HudElement, int[]>();
    private static final Map<String, Double> luma = new LinkedHashMap<String, Double>();
    private static Set<String> lockedWere;
    private static boolean started;
    private static long timeWas;
    private static DraggableHudScreen189 editor;
    private static LadsSettingsScreen189 menu;
    private static Map<String, int[]> layout;
    private static double lastLuma, dragX;
    private static int dragStep;
    private static boolean dragOk;
    private static final Set<String> dragValues = new LinkedHashSet<String>();

    private Probe172Ui() {}

    static {
        STEPS.add(Probe172Ui::setup);
        if (!DEFER) {
            hudEditor();
            moduleReset();
        }
        fullbright();
        STEPS.add(Probe172Ui::finish);
    }

    private static boolean setup(Minecraft mc) {
        started = true;
        for (String name : FIXTURE) remember(Options189.module(name));
        remember(Options189.module("Fullbright"));
        HudSettings settings = HudSettings.getInstance();
        for (Map.Entry<String, int[]> entry : settings.getPositions().entrySet()) positionsWere.put(entry.getKey(), entry.getValue().clone());
        for (Set<String> group : settings.getGroups()) groupsWere.add(new HashSet<String>(group));
        lockedWere = new HashSet<String>(settings.getLocked());
        for (HudElement element : HudManager.getInstance().getElements()) elementsWere.put(element, new int[] {element.getX(), element.getY()});
        Fullbright189.qaDefer = DEFER;
        LOG.info("Lads UI capture BEGIN: real LWJGL input events{}", DEFER ? " (CONTROL: the lightmap is left to the game's own tick)" : "");
        return after(1);
    }

    private static void remember(Module module) {
        enabledWere.put(module, module.isEnabled());
        modifiedWere.put(module, module.getLastModified());
        for (Option option : module.getOptions()) optionsWere.put(option, option.save());
    }

    // ---- 1. The HUD editor's Reset ----
    private static void hudEditor() {
        STEPS.add(mc -> {
            HudSettings settings = HudSettings.getInstance();
            for (String name : FIXTURE) Options189.module(name).setEnabled(true);
            settings.getPositions().clear();
            settings.replaceGroups(new ArrayList<Set<String>>());
            settings.replaceLocked(new HashSet<String>());
            settings.setPosition("CPS", 12, 42);
            settings.setPosition("Day", 12, 78);
            settings.setPosition("Health", 12, 112);
            settings.setPosition("FPS", 200, 20);
            editor = new DraggableHudScreen189(null);
            mc.displayGuiScreen(editor);
            return after(30);
        });
        STEPS.add(mc -> {
            check(hud().boundsFor("CPS") != null, "the HUD editor shows the fixture layout");
            layout = snapshot();
            screenshot(mc, "ui172-hud-1-before");
            return pressHud("reset", 4);
        });
        STEPS.add(mc -> {
            check(hud().confirmDialog().isOpen(), "Reset opens the question");
            check(sameLayout(), "asking changed nothing");
            screenshot(mc, "ui172-hud-2-dialog");
            clickRect(hud().confirmDialog().cancelBounds());
            return after(4);
        });
        STEPS.add(mc -> {
            check(!hud().confirmDialog().isOpen() && mc.currentScreen == editor, "Cancel closes the question and keeps the editor");
            check(sameLayout(), "Cancel leaves the layout untouched");
            screenshot(mc, "ui172-hud-3-cancelled");
            return pressHud("reset", 3);
        });
        STEPS.add(mc -> { tap(Keyboard.KEY_ESCAPE, (char) 27); return after(3); });
        STEPS.add(mc -> {
            check(!hud().confirmDialog().isOpen() && mc.currentScreen == editor, "Esc cancels the question and does not leave the editor");
            check(sameLayout(), "Esc leaves the layout untouched");
            return pressHud("reset", 3);
        });
        STEPS.add(mc -> { clickRect(hud().confirmDialog().confirmBounds()); return after(30); });
        STEPS.add(mc -> {
            check(!hud().confirmDialog().isOpen() && HudSettings.getInstance().getPositions().isEmpty(), "Confirm resets every HUD position");
            screenshot(mc, "ui172-hud-4-confirmed");
            HudSettings.getInstance().setPosition("CPS", 12, 42);
            return after(3);
        });
        STEPS.add(mc -> pressHud("reset", 3));
        STEPS.add(mc -> { tap(Keyboard.KEY_RETURN, '\r'); return after(4); });
        STEPS.add(mc -> {
            check(!hud().confirmDialog().isOpen() && HudSettings.getInstance().getPositions().isEmpty(), "Enter confirms: the layout is reset");
            mc.displayGuiScreen(null);
            return after(5);
        });
    }

    // ---- 2. A HUD module's Reset options ----
    private static void moduleReset() {
        STEPS.add(mc -> {
            ((SliderOption) Options189.module("CPS").getOption("Size")).setValue(150);
            menu = new LadsSettingsScreen189(null);
            mc.displayGuiScreen(menu);
            menu.openModule("CPS");
            return after(20);
        });
        STEPS.add(mc -> {
            check(size() == 150 && ui().controlBounds("reset") != null, "the CPS page shows Size 150 and its Reset options button");
            screenshot(mc, "ui172-module-1-before");
            return pressMenu("reset", 4);
        });
        STEPS.add(mc -> {
            check(ui().confirmDialog().isOpen() && size() == 150, "Reset options opens the question, Size still 150");
            screenshot(mc, "ui172-module-2-dialog");
            clickRect(ui().confirmDialog().cancelBounds());
            return after(4);
        });
        STEPS.add(mc -> {
            check(!ui().confirmDialog().isOpen() && size() == 150 && mc.currentScreen == menu, "Cancel keeps the options and the page");
            screenshot(mc, "ui172-module-3-cancelled");
            return pressMenu("reset", 3);
        });
        STEPS.add(mc -> { tap(Keyboard.KEY_ESCAPE, (char) 27); return after(3); });
        STEPS.add(mc -> {
            check(!ui().confirmDialog().isOpen() && size() == 150 && mc.currentScreen == menu, "Esc cancels the question, not the page; Size is still 150");
            return pressMenu("reset", 3);
        });
        STEPS.add(mc -> { clickRect(ui().confirmDialog().confirmBounds()); return after(10); });
        STEPS.add(mc -> {
            check(!ui().confirmDialog().isOpen() && size() == 100, "Confirm resets the module's options (Size back to 100)");
            screenshot(mc, "ui172-module-4-confirmed");
            ((SliderOption) Options189.module("CPS").getOption("Size")).setValue(150);
            return after(3);
        });
        STEPS.add(mc -> pressMenu("reset", 3));
        STEPS.add(mc -> { tap(Keyboard.KEY_RETURN, '\r'); return after(4); });
        STEPS.add(mc -> {
            check(size() == 100, "Enter confirms: Size back to 100");
            mc.displayGuiScreen(null);
            return after(5);
        });
    }

    // ---- 3. Fullbright, menu open ----
    private static void fullbright() {
        STEPS.add(mc -> {
            WorldServer world = mc.getIntegratedServer().worldServerForDimension(0);
            timeWas = world.getWorldTime();
            mc.getIntegratedServer().addScheduledTask(() -> world.setWorldTime(18000));
            Options189.module("Fullbright").setEnabled(true);
            gamma().setValue(0);
            menu = new LadsSettingsScreen189(null);
            mc.displayGuiScreen(menu);
            menu.openModule("Fullbright");
            return after(60);
        });
        STEPS.add(mc -> {
            check(ui().gameViewBounds() != null, "the Fullbright page draws the live world in its preview");
            check(mc.getIntegratedServer() != null && mc.getIntegratedServer().worldServerForDimension(0).getWorldTime() % 24000L > 16500, "the QA world is at night");
            shot(mc, "ui172-fb-p000");
            return after(1);
        });
        for (final int percent : new int[] {30, 65, 100, 0}) {
            STEPS.add(mc -> { drag(percent); return after(3); });
            STEPS.add(mc -> {
                check(gamma().getValue() == percent && mc.currentScreen == menu, "the slider dragged to " + percent + "% stands at " + gamma().getValue() + " and the menu is still open");
                shot(mc, String.format(Locale.ROOT, "ui172-fb-p%03d", percent) + (percent == 0 ? "-again" : ""));
                return after(1);
            });
        }
        // A drag across the whole track, one move per tick: every value the slider takes on the way.
        STEPS.add(mc -> {
            LadsSettingsScreen.Rect track = ui().controlBounds("option:Gamma");
            if (dragStep == 0) {
                dragX = track.x() + 4;
                mouse(0, true, (int) dragX, track.y() + track.height() / 2);
                dragOk = true;
                dragStep = 1;
                return retry(1);
            }
            double value = gamma().getValue();
            dragOk &= value % 5 == 0;
            dragValues.add(gamma().display());
            if (dragX < track.x() + track.width() - 4) {
                dragX += 2;
                mouse(-1, false, (int) dragX, track.y() + track.height() / 2);
                return retry(0);
            }
            mouse(0, false, (int) dragX, track.y() + track.height() / 2);
            LOG.info("Lads UI capture slider positions along one drag: {}", dragValues.toString().replace(",", ""));
            check(dragOk && dragValues.size() > 5, "every position of a drag from 0 to 100 was a multiple of 5 (" + dragValues.size() + " positions)");
            return after(3);
        });
        STEPS.add(mc -> { typed("40"); return after(3); });
        STEPS.add(mc -> {
            check(gamma().getValue() == 40, "40 typed in the field (Enter) is 40%");
            shot(mc, "ui172-fb-typed-040");
            typed("67");
            return after(3);
        });
        STEPS.add(mc -> { check(gamma().getValue() == 65, "67 typed lands on the nearest step, 65%"); typed("250"); return after(3); });
        STEPS.add(mc -> { check(gamma().getValue() == 65, "250 typed (out of range) is refused: the value stays 65%"); typed("abc"); return after(3); });
        STEPS.add(mc -> {
            check(gamma().getValue() == 65 && !ui().isEditingText(), "letters typed are ignored and the value stays 65%");
            if (!DEFER) {
                double l0 = luma.get("ui172-fb-p000"), l30 = luma.get("ui172-fb-p030"), l65 = luma.get("ui172-fb-p065"), l100 = luma.get("ui172-fb-p100");
                LOG.info("Lads UI capture world brightness (mean of the preview, 0-255) 0%: {} 30%: {} 65%: {} 100%: {}", f(l0), f(l30), f(l65), f(l100));
                check(l30 > l0 + 3 && l65 >= l30 && l100 >= l65 && l100 > l0 + 15, "the world under the open menu follows the slider: brighter at each higher setting");
                check(Math.abs(luma.get("ui172-fb-p000-again") - l0) < 2, "back at 0% the world is as dark as at the start");
                return after(1);
            }
            check(Math.abs(luma.get("ui172-fb-p100") - luma.get("ui172-fb-p000")) < 2, "CONTROL: the world under the open menu did not change at any setting");
            mc.displayGuiScreen(null);
            return after(60);
        });
        STEPS.add(mc -> {
            if (!DEFER) return after(1);
            worldShot(mc, "ui172-fb-menu-closed");
            check(lastLuma > luma.get("ui172-fb-p000") + 15, "CONTROL: after the menu closed the world is brighter: " + f(lastLuma));
            return after(1);
        });
    }

    private static boolean finish(Minecraft mc) {
        stop();
        return after(5);
    }

    /** Puts everything back: modules and options, the HUD layout, the clock; the sandbox's config is saved as it was. */
    static void stop() {
        if (!started) return;
        started = false;
        Minecraft mc = Minecraft.getMinecraft();
        Fullbright189.qaDefer = false;
        if (mc.currentScreen == editor || mc.currentScreen == menu) mc.displayGuiScreen(null);
        for (Map.Entry<Option, JsonElement> entry : optionsWere.entrySet()) entry.getKey().load(entry.getValue());
        for (Map.Entry<Module, Boolean> entry : enabledWere.entrySet()) entry.getKey().setEnabled(entry.getValue());
        for (Map.Entry<Module, Long> entry : modifiedWere.entrySet()) entry.getKey().setLastModified(entry.getValue());
        HudSettings settings = HudSettings.getInstance();
        settings.getPositions().clear();
        for (Map.Entry<String, int[]> entry : positionsWere.entrySet()) settings.setPosition(entry.getKey(), entry.getValue()[0], entry.getValue()[1]);
        settings.replaceGroups(groupsWere);
        settings.replaceLocked(lockedWere);
        for (Map.Entry<HudElement, int[]> entry : elementsWere.entrySet()) {
            entry.getKey().endPositionEdit();
            entry.getKey().setPosition(entry.getValue()[0], entry.getValue()[1]);
            entry.getKey().restoreSavedPosition();
        }
        ConfigManager.save();
        if (mc.getIntegratedServer() != null && timeWas != 0) {
            final WorldServer world = mc.getIntegratedServer().worldServerForDimension(0);
            final long time = timeWas;
            mc.getIntegratedServer().addScheduledTask(() -> world.setWorldTime(time));
        }
    }

    private static SliderOption gamma() { return (SliderOption) Options189.module("Fullbright").getOption("Gamma"); }
    private static LadsSettingsScreen ui() { return menu.ui(); }
    private static DraggableHudScreen hud() { return editor.ui(); }
    private static int size() { return (int) ((SliderOption) Options189.module("CPS").getOption("Size")).getValue(); }
    private static String f(double value) { return String.format(Locale.ROOT, "%.1f", value); }

    private static Map<String, int[]> snapshot() {
        Map<String, int[]> now = new HashMap<String, int[]>();
        for (Map.Entry<String, int[]> entry : HudSettings.getInstance().getPositions().entrySet()) now.put(entry.getKey(), entry.getValue().clone());
        return now;
    }

    private static boolean sameLayout() {
        Map<String, int[]> now = snapshot();
        if (now.size() != layout.size()) return false;
        for (Map.Entry<String, int[]> entry : layout.entrySet()) if (!Arrays.equals(entry.getValue(), now.get(entry.getKey()))) return false;
        return true;
    }

    /** A real click on one of the HUD editor's buttons. */
    private static boolean pressHud(String id, int ticksAfter) throws Exception {
        for (DraggableHudScreen.Control control : hud().controls()) {
            if (!control.id().equals(id)) continue;
            check(control.enabled(), "button enabled: " + id);
            clickRect(control.bounds());
            return after(ticksAfter);
        }
        throw new IllegalStateException("no HUD editor control " + id);
    }

    private static boolean pressMenu(String id, int ticksAfter) throws Exception {
        LadsSettingsScreen.Rect r = ui().controlBounds(id);
        check(r != null, "control drawn: " + id);
        click(r.x() + r.width() / 2, r.y() + r.height() / 2);
        return after(ticksAfter);
    }

    private static void clickRect(HudGroupLayout.Rect r) throws Exception {
        click(r.x() + r.width() / 2, r.y() + r.height() / 2);
    }

    /** Presses the slider in its middle, drags it to this percentage in steps and releases. */
    private static void drag(int percent) throws Exception {
        LadsSettingsScreen.Rect track = ui().controlBounds("option:Gamma");
        int y = track.y() + track.height() / 2, from = track.x() + track.width() / 2;
        int to = (int) Math.round(track.x() + 4 + (track.width() - 8) * percent / 100.0);
        mouse(0, true, from, y);
        for (int i = 1; i <= 4; i++) mouse(-1, false, from + (to - from) * i / 4, y);
        mouse(0, false, to, y);
    }

    /** Clicks the value field, types the text key by key and presses Enter. */
    private static void typed(String text) throws Exception {
        LadsSettingsScreen.Rect field = ui().controlBounds("option:Gamma:field");
        click(field.x() + field.width() / 2, field.y() + field.height() / 2);
        for (char c : text.toCharArray()) tap(c >= '0' && c <= '9' ? Keyboard.KEY_1 + (c == '0' ? 9 : c - '1') : c == 'a' ? Keyboard.KEY_A : c == 'b' ? Keyboard.KEY_B : Keyboard.KEY_C, c);
        tap(Keyboard.KEY_RETURN, '\r');
    }

    /** The screenshot, and the mean RGB (0-255) of the preview's world in it. */
    private static void shot(Minecraft mc, String name) throws Exception {
        LadsSettingsScreen.Rect view = ui().gameViewBounds();
        double scale = mc.displayWidth / (double) menu.width;
        measure(mc, name, (int) (view.x() * scale) + 3, (int) (view.y() * scale) + 3, (int) ((view.x() + view.width()) * scale) - 3, (int) ((view.y() + view.height()) * scale) - 3);
    }

    /** The screenshot of the game with no menu, and the mean RGB of its world (the HUD at the bottom left out). */
    private static void worldShot(Minecraft mc, String name) throws Exception {
        measure(mc, name, 3, 3, mc.displayWidth - 3, mc.displayHeight * 85 / 100 - 3);
    }

    private static void measure(Minecraft mc, String name, int x0, int y0, int x1, int y1) throws Exception {
        screenshot(mc, name);
        BufferedImage image = ImageIO.read(new File(mc.mcDataDir, "lads-qa/screenshots/" + name + ".png"));
        long sum = 0, count = 0;
        for (int y = Math.max(0, y0); y < Math.min(image.getHeight(), y1); y += 2)
            for (int x = Math.max(0, x0); x < Math.min(image.getWidth(), x1); x += 2) {
                int c = image.getRGB(x, y);
                sum += (c & 0xFF) + (c >> 8 & 0xFF) + (c >> 16 & 0xFF);
                count += 3;
            }
        lastLuma = count == 0 ? 0 : (double) sum / count;
        luma.put(name, lastLuma);
        LOG.info("Lads UI frame {} (world brightness {})", name, f(lastLuma));
    }
}
