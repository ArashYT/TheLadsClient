package com.thelads.core.client.hud;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.core.client.gui.DraggableHudScreen;
import com.thelads.core.client.gui.LadsSettingsScreen;
import com.thelads.core.client.util.ClientPaths;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only, the same on every version (the HUD layout probe's driver runs it when LADS_QA_BGSHADOW=1 or -Dthelads.qaBgShadow=true):
 * 1.9.11's Backgrounds and Text Shadow buttons, beside Colors in the settings header and above Colors/Reset in the HUD editor. Each is
 * clicked through the screen's own mouse handlers at the QA window (GUI scale 2), at 1600 x 900 and 854 x 480 (GUI 2) and at 960 x 720
 * (GUI 3, the narrowest layout the game allows); after every click the setting and thelads_config.json (hud.backgrounds / hud.textShadow /
 * hud.softShadow) must agree, the header buttons must not overlap each other or "THE LADS", the editor rows must not overlap.
 * In the world the HUD is photographed after each header click (bgs-hud-*.png, its bounds logged for pixel comparison). Markers are
 * the HUD layout probe's ("Lads hud layout check PASS/FAIL", "Lads hud layout END: N passed, M failed").
 */
public final class BgShadowQa {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final List<String> SHOWN = List.of("FPS", "Coordinates", "CPS", "Day", "Memory", "Keystrokes");
    private static final List<String> HEADER = List.of("global-hud-fps", "global-backgrounds", "global-text-shadow", "global-colors", "close");
    private static final List<String> FAILURES = new ArrayList<>();
    private static final Map<Module, Boolean> ENABLED_WERE = new LinkedHashMap<>();
    private static final List<Object[]> STEPS = new ArrayList<>(); // {Runnable action, Integer waitAfter}
    private static int step = -1, wait, passed;
    private static boolean bgWas, shadowWas, softWas;
    private static int scaleWas;

    private BgShadowQa() {}

    public static boolean wanted() {
        return "1".equals(System.getenv("LADS_QA_BGSHADOW")) || Boolean.getBoolean("thelads.qaBgShadow");
    }

    public static int failed() { return FAILURES.size(); }

    /** Each client tick of the probe; true once the sequence has ended. */
    public static boolean tick(HudLayoutQa.Driver d) {
        if (step == -1) { begin(d); step = 0; wait = 60; return false; }
        if (wait > 0) { wait--; return false; }
        if (step >= STEPS.size()) { end(d); return true; }
        Object[] s = STEPS.get(step++);
        try { ((Runnable) s[0]).run(); }
        catch (RuntimeException failure) { fail("step " + step + ": " + failure); LOGGER.error("Lads hud layout check FAIL: bgshadow step {}", step, failure); }
        wait = (Integer) s[1];
        return false;
    }

    private static void add(int waitAfter, Runnable action) { STEPS.add(new Object[] {action, waitAfter}); }

    private static void begin(HudLayoutQa.Driver d) {
        HudSettings s = HudSettings.getInstance();
        bgWas = s.isBackgrounds(); shadowWas = s.isTextShadow(); softWas = s.isSoftShadow(); scaleWas = s.getHudScale();
        for (HudElement element : HudManager.getInstance().getElements()) {
            Module module = element.getModuleName() == null ? null : ModuleManager.getInstance().getModule(element.getModuleName());
            if (module == null) continue;
            ENABLED_WERE.put(module, module.isEnabled());
            module.setEnabled(SHOWN.contains(module.getName()));
        }
        Module autohide = ModuleManager.getInstance().getModule("Autohide");
        if (autohide != null) { ENABLED_WERE.put(autohide, autohide.isEnabled()); autohide.setEnabled(false); }
        s.setHudScale(0);
        s.setBackgrounds(false); s.setTextShadow(false); s.setSoftShadow(false);
        d.command("gamemode creative @a");
        LOGGER.info("Lads hud layout BEGIN: backgrounds / text shadow buttons");

        // The QA window at GUI scale 2: the HUD photographed after each header click.
        add(40, () -> { d.window(0, 0); d.guiScale(2); }); // window(0, 0): the window as it is (1.8.9 also clears its tutorial toast)
        add(10, () -> { logBounds(); shot(d, "bgs-hud-1-bg-off-shadow-off"); });
        add(30, d::openSettings);
        add(10, () -> header(d, "qa"));
        add(10, () -> shot(d, "bgs-qa-1-settings-bg-off-shadow-off"));
        add(10, () -> click(d, "global-backgrounds", true, false, false));
        add(10, () -> shot(d, "bgs-qa-2-settings-bg-on"));
        add(30, d::closeScreen);
        add(10, () -> shot(d, "bgs-hud-2-bg-on-shadow-off"));
        add(30, d::openSettings);
        add(10, () -> click(d, "global-backgrounds", false, false, false));
        add(10, () -> click(d, "global-text-shadow", false, true, false));
        add(10, () -> shot(d, "bgs-qa-4-settings-shadow-on"));
        add(30, d::closeScreen);
        add(10, () -> shot(d, "bgs-hud-4-bg-off-shadow-on"));
        add(30, d::openSettings);
        add(10, () -> click(d, "global-backgrounds", true, true, false));
        add(30, d::closeScreen);
        add(10, () -> shot(d, "bgs-hud-5-bg-on-shadow-on"));
        add(30, d::openSettings);
        add(10, () -> click(d, "global-text-shadow", true, false, false));
        add(10, () -> click(d, "global-backgrounds", false, false, false));
        add(10, () -> click(d, "global-colors"));
        add(10, () -> { expect("the Colors popup is open", d.settings().isEditingText(), ""); shot(d, "bgs-qa-5-colors-popup"); });
        add(30, d::closeScreen);
        editor(d, "qa");
        // A wide window (full labels), the classic small window, then the narrowest layout the game allows (320 x 240 GUI pixels).
        phase(d, "wide", 1600, 900, 2);
        phase(d, "win854", 854, 480, 2);
        phase(d, "gui3", 960, 720, 3);
        add(30, () -> { d.closeScreen(); d.window(0, 0); d.guiScale(2); });
    }

    private static void phase(HudLayoutQa.Driver d, String name, int w, int h, int scale) {
        // The window first: the game clamps GUI scale to what the current window allows.
        add(40, () -> { d.closeScreen(); d.window(w, h); });
        add(30, () -> d.guiScale(scale));
        add(30, d::openSettings);
        add(10, () -> header(d, name));
        add(10, () -> shot(d, "bgs-" + name + "-1-settings"));
        add(10, () -> click(d, "global-backgrounds", true, false, false));
        add(10, () -> click(d, "global-text-shadow", true, true, false));
        add(10, () -> shot(d, "bgs-" + name + "-2-settings-bg-on-shadow-on"));
        add(10, () -> click(d, "global-text-shadow", true, false, false));
        add(10, () -> click(d, "global-backgrounds", false, false, false));
        add(10, () -> click(d, "global-colors"));
        add(10, () -> shot(d, "bgs-" + name + "-3-colors-popup"));
        add(30, d::closeScreen);
        editor(d, name);
    }

    /** The HUD editor: the two rows above Colors/Reset, clicked through the editor's mouse handlers. */
    private static void editor(HudLayoutQa.Driver d, String name) {
        add(40, d::openEditor);
        add(10, () -> editorLayout(d, name));
        add(10, () -> shot(d, "bgs-" + name + "-6-editor"));
        add(10, () -> editorClick(d, "backgrounds", true, false, false));
        add(10, () -> editorClick(d, "textshadow", true, true, false));
        add(10, () -> shot(d, "bgs-" + name + "-8-editor-shadow-on"));
        add(10, () -> editorClick(d, "textshadow", true, false, false));
        add(10, () -> editorClick(d, "backgrounds", false, false, false));
        add(30, d::closeScreen);
    }

    private static void header(HudLayoutQa.Driver d, String name) {
        LadsSettingsScreen ui = settings(d);
        List<LadsSettingsScreen.Rect> rects = new ArrayList<>();
        for (String id : HEADER) {
            LadsSettingsScreen.Rect r = ui.controlBounds(id);
            expect(name + ": header button " + id + " is drawn", r != null, "");
            if (r != null) rects.add(r);
        }
        LOGGER.info("Lads hud layout bgshadow {} header {}", name, rects);
        if (rects.size() != HEADER.size()) return;
        for (int i = 0; i < rects.size(); i++) for (int j = i + 1; j < rects.size(); j++)
            expect(name + ": " + HEADER.get(i) + " and " + HEADER.get(j) + " do not overlap", !overlaps(rects.get(i), rects.get(j)), rects.get(i) + " " + rects.get(j));
        LadsSettingsScreen.Rect close = rects.get(4), fps = rects.get(0);
        int right = close.x() + close.width(), pad = right + 10 < 450 ? 10 : 20;
        LOGGER.info("Lads hud layout bgshadow {} screen {} GUI px wide", name, right + pad);
        // Every button inside the screen's padding; "THE LADS" (x = pad, about 46 GUI px) is drawn only when HUD FPS leaves it room.
        expect(name + ": HUD FPS is inside the screen", fps.x() >= pad, "fps " + fps + " pad " + pad);
        LOGGER.info("Lads hud layout bgshadow {} THE LADS {} (HUD FPS at x {})", name, fps.x() >= pad + 52 ? "has room" : "gives way", fps.x());
        boolean narrow = rects.get(1).width() < 40;
        LOGGER.info("Lads hud layout bgshadow {} labels {} (Backgrounds button {} wide)", name, narrow ? "short (BG / Shadow)" : "full", rects.get(1).width());
    }

    private static void editorLayout(HudLayoutQa.Driver d, String name) {
        DraggableHudScreen ui = d.editor();
        if (ui == null) throw new IllegalStateException("the HUD editor is not open");
        List<DraggableHudScreen.Control> controls = ui.controls();
        Map<String, HudGroupLayout.Rect> byId = new LinkedHashMap<>();
        for (DraggableHudScreen.Control c : controls) byId.put(c.id(), c.bounds());
        for (String id : List.of("hudscale", "backgrounds", "textshadow", "colors", "reset"))
            expect(name + ": editor control " + id + " is drawn", byId.containsKey(id), byId.keySet().toString());
        List<String> ids = new ArrayList<>(byId.keySet());
        for (int i = 0; i < ids.size(); i++) for (int j = i + 1; j < ids.size(); j++)
            if (byId.get(ids.get(i)).intersects(byId.get(ids.get(j))))
                expect(name + ": editor " + ids.get(i) + " and " + ids.get(j) + " do not overlap", false, byId.get(ids.get(i)) + " " + byId.get(ids.get(j)));
        // Module rows are clipped to the list (scissor), so a row reaching past it is not drawn over HUD scale: judged on the frames.
        LOGGER.info("Lads hud layout bgshadow {} editor {}", name, byId);
    }

    private static LadsSettingsScreen settings(HudLayoutQa.Driver d) {
        LadsSettingsScreen ui = d.settings();
        if (ui == null) throw new IllegalStateException("the Lads settings screen is not open");
        return ui;
    }

    private static void click(HudLayoutQa.Driver d, String id) {
        LadsSettingsScreen ui = settings(d);
        LadsSettingsScreen.Rect r = ui.controlBounds(id);
        if (r == null) throw new IllegalStateException("no header control " + id);
        double x = r.x() + r.width() / 2.0, y = r.y() + r.height() / 2.0;
        ui.mouseClicked(x, y, 0);
        ui.mouseReleased(x, y, 0);
    }

    private static void click(HudLayoutQa.Driver d, String id, boolean bg, boolean shadow, boolean soft) {
        click(d, id);
        state("settings " + id, bg, shadow, soft);
    }

    private static void editorClick(HudLayoutQa.Driver d, String id, boolean bg, boolean shadow, boolean soft) {
        DraggableHudScreen ui = d.editor();
        if (ui == null) throw new IllegalStateException("the HUD editor is not open");
        for (DraggableHudScreen.Control c : ui.controls()) if (c.id().equals(id)) {
            double x = c.bounds().x() + c.bounds().width() / 2.0, y = c.bounds().y() + c.bounds().height() / 2.0;
            ui.mouseClicked(x, y, 0, 0);
            ui.mouseReleased(x, y, 0, 0);
            LOGGER.info("Lads hud layout bgshadow editor clicked {} (label was \"{}\")", id, c.label());
            state("editor " + id, bg, shadow, soft);
            return;
        }
        throw new IllegalStateException("no editor control " + id);
    }

    /** The live setting and the saved file both match. */
    private static void state(String what, boolean bg, boolean shadow, boolean soft) {
        HudSettings s = HudSettings.getInstance();
        String live = "backgrounds=" + s.isBackgrounds() + " textShadow=" + s.isTextShadow() + " softShadow=" + s.isSoftShadow();
        expect(what + " -> live " + live, s.isBackgrounds() == bg && s.isTextShadow() == shadow && (!shadow || s.isSoftShadow() == soft), "");
        try {
            JsonObject hud = JsonParser.parseString(new String(Files.readAllBytes(ClientPaths.getConfigFile().toPath()), StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("hud");
            boolean fb = hud.get("backgrounds").getAsBoolean(), fs = hud.get("textShadow").getAsBoolean(), fso = hud.get("softShadow").getAsBoolean();
            expect(what + " -> thelads_config.json hud.backgrounds=" + fb + " textShadow=" + fs + " softShadow=" + fso,
                fb == bg && fs == shadow && (!shadow || fso == soft), ClientPaths.getConfigFile().toString());
        } catch (Exception failure) {
            expect(what + " -> thelads_config.json readable", false, failure.toString());
        }
    }

    /** The drawn HUD's bounds in GUI pixels, so the HUD shots can be compared pixel by pixel. */
    private static void logBounds() {
        HudManager.afterRender = g -> {
            HudManager.afterRender = null;
            StringBuilder b = new StringBuilder();
            for (HudElement e : HudManager.getInstance().getElements())
                if (e.getModuleName() != null && SHOWN.contains(e.getModuleName())) b.append(' ').append(e.getModuleName()).append('=').append(e.measureBounds(g, false));
            LOGGER.info("Lads hud layout bgshadow bounds gui {}x{}:{}", g.getScaledWidth(), g.getScaledHeight(), b);
        };
    }

    private static boolean overlaps(LadsSettingsScreen.Rect a, LadsSettingsScreen.Rect b) {
        return a.x() < b.x() + b.width() && b.x() < a.x() + a.width() && a.y() < b.y() + b.height() && b.y() < a.y() + a.height();
    }

    private static void expect(String what, boolean ok, String detail) {
        if (ok) { passed++; LOGGER.info("Lads hud layout check PASS: {}", what); }
        else { fail(what + (detail.isEmpty() ? "" : " (" + detail + ")")); }
    }

    private static void fail(String what) {
        FAILURES.add(what);
        LOGGER.error("Lads hud layout check FAIL: {}", what);
    }

    private static void shot(HudLayoutQa.Driver d, String name) {
        d.shot(name);
        LOGGER.info("Lads hud layout frame {}", name);
    }

    private static void end(HudLayoutQa.Driver d) {
        step = Integer.MAX_VALUE;
        HudManager.afterRender = null;
        try {
            d.closeScreen();
            HudSettings s = HudSettings.getInstance();
            s.setBackgrounds(bgWas); s.setTextShadow(shadowWas); s.setSoftShadow(softWas); s.setHudScale(scaleWas);
            ENABLED_WERE.forEach(Module::setEnabled);
        } catch (RuntimeException failure) {
            FAILURES.add("restoring: " + failure);
        }
        if (!FAILURES.isEmpty()) LOGGER.error("Lads hud layout FAILED: {}", String.join(" | ", FAILURES));
        LOGGER.info("Lads hud layout END: {} passed, {} failed", passed, FAILURES.size());
    }
}
