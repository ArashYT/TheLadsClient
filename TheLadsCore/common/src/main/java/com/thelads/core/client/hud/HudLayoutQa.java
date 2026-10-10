package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.gui.DraggableHudScreen;
import com.thelads.core.client.hud.HudGroupLayout.Rect;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only, the same on every version (its HUD layout probe drives it through a Driver; inert otherwise): the 1.9.0 HUD layout
 * sequence. A layout saved before 1.9.0 at GUI scale 3 is loaded (Day and CPS beside the hotbar, Coordinates docked under Potion
 * Effects, the Scoreboard docked under the Minimap, or FPS where there is no minimap) and photographed at GUI scales 3 and 2,
 * with more potion effects and scoreboard lines (the docked HUDs are pushed), at a separate Lads HUD scale, and in the HUD editor
 * with Day dragged onto the hotbar (mid-drag: snap guides and the hotbar's box), then in game. Every frame is checked and logged
 * ("Lads hud layout check PASS/FAIL"), the run ends with "Lads hud layout END: N passed, M failed".
 */
public final class HudLayoutQa {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");

    /** What a version does for the sequence. */
    public interface Driver {
        /** The game window's client size in pixels; 0, 0 puts back the size it had. */
        void window(int width, int height);
        /** Vanilla's GUI scale option, applied at once. */
        void guiScale(int scale);
        /** A command run by the integrated server (no chat feedback; target the player with @a), without the slash. */
        void command(String command);
        /** Gives the player a potion effect for 5 minutes (name as 1.20.1 has it, id as 1.8.9 has it); null clears them all. */
        void effect(String name, int id);
        /** Writes the last finished frame to screenshots/name.png. */
        void shot(String name);
        void openEditor();
        /** The open HUD editor's controller, or null. */
        DraggableHudScreen editor();
        void closeScreen();
    }

    private static int step = -1, wait, passed;
    private static final List<String> FAILURES = new ArrayList<>();
    private static final Map<String, int[]> POSITIONS_WERE = new HashMap<>();
    private static final Map<String, HudSettings.Anchor> ANCHORS_WERE = new HashMap<>();
    private static final List<Set<String>> GROUPS_WERE = new ArrayList<>();
    private static final Map<Module, Boolean> ENABLED_WERE = new LinkedHashMap<>();
    private static final Set<String> LOCKED_WERE = new HashSet<>();
    private static int scaleWas;
    /** The HUD the Scoreboard is docked under, and the room Potion Effects had in the editor (its 3-effect sample). */
    private static String top;
    private static int potionRoom;
    private static boolean onHotbar;

    private HudLayoutQa() {}

    public static boolean running() { return step >= 0 && step < 99; }

    /** The checks that failed (after the end). */
    public static int failed() { return FAILURES.size(); }

    /** Each client tick of the probe: starts the sequence, then steps it; true once it has ended. */
    public static boolean tick(Driver d) {
        try {
            if (step < 0) { begin(d); step = -2; wait = 40; return false; }
            if (wait > 0) { wait--; return false; }
            if (step == -2) { d.guiScale(3); step = 0; wait = 40; return false; } // once the window has its new size
            switch (step++) {
                case 0 -> { afterFrame("loading the layout", HudLayoutQa::load); wait = 20; }
                case 1 -> check("GUI scale 3, as saved before 1.9.0", 5);
                case 2 -> shot(d, "hud-1-scale3-saved");
                case 3 -> { d.guiScale(2); wait = 40; }
                case 4 -> check("GUI scale 2", 5);
                case 5 -> shot(d, "hud-2-scale2");
                case 6 -> {
                    String[] names = {"speed", "haste", "strength", "jump_boost", "regeneration", "resistance", "fire_resistance", "night_vision"};
                    int[] ids = {1, 3, 5, 8, 10, 11, 12, 16};
                    for (int i = 2; i < names.length; i++) d.effect(names[i], ids[i]);
                    for (String player : new String[] {"Gamma", "Delta", "Epsilon", "Zeta", "Eta", "Theta", "Iota", "Kappa"})
                        d.command("scoreboard players set " + player + " ladsqa 1");
                    wait = 50;
                }
                case 7 -> check("GUI scale 2, 8 effects and 10 scoreboard lines", 5);
                case 8 -> shot(d, "hud-3-scale2-pushed");
                case 9 -> { d.guiScale(3); wait = 40; }
                case 10 -> check("GUI scale 3, 8 effects and 10 scoreboard lines", 5);
                case 11 -> shot(d, "hud-4-scale3-pushed");
                case 12 -> { HudSettings.getInstance().setHudScale(2); wait = 20; }
                case 13 -> check("Lads HUD scale 2 under GUI scale 3", 5);
                case 14 -> shot(d, "hud-5-lads-scale2");
                case 15 -> { HudSettings.getInstance().setHudScale(0); d.openEditor(); wait = 50; }
                case 16 -> shot(d, "hud-6-editor");
                case 17 -> { drag(d.editor(), false); wait = 10; }
                case 18 -> shot(d, "hud-7-editor-snap-guides");
                case 19 -> { drag(d.editor(), true); onHotbar = true; wait = 10; }
                case 20 -> {
                    DraggableHudScreen ui = d.editor();
                    Rect day = ui == null ? null : ui.boundsFor("Day"), hotbar = ui == null ? null : ui.hotbarBounds();
                    expect("editor", "Day dropped flush on top of the hotbar holds on to it",
                        day != null && day.bottom() == hotbar.y() && HudSettings.HOTBAR.equals(HudAnchors.holder("Day")), day + " hotbar " + hotbar + " holder " + HudAnchors.holder("Day"));
                    shot(d, "hud-8-editor-dropped");
                }
                case 21 -> { d.closeScreen(); wait = 30; }
                case 22 -> check("in game after the drop", 5);
                case 23 -> shot(d, "hud-9-after-drop");
                default -> { end(d); return true; }
            }
        } catch (RuntimeException failure) {
            FAILURES.add("step " + step + ": " + failure);
            LOGGER.error("Lads hud layout FAILED at step {}", step, failure);
            end(d);
            return true;
        }
        return false;
    }

    private static void shot(Driver d, String name) {
        d.shot(name);
        LOGGER.info("Lads hud layout frame {}", name);
        wait = 10;
    }

    private static void begin(Driver d) {
        HudSettings settings = HudSettings.getInstance();
        POSITIONS_WERE.putAll(settings.getPositions());
        ANCHORS_WERE.putAll(settings.getAnchors());
        settings.getGroups().forEach(group -> GROUPS_WERE.add(Set.copyOf(group)));
        LOCKED_WERE.addAll(settings.getLocked());
        scaleWas = settings.getHudScale();
        boolean minimap = HudManager.getInstance().getElements().stream().anyMatch(e -> "Minimap".equals(e.getModuleName()) && e.isAvailable());
        top = minimap ? "Minimap" : "FPS";
        Set<String> ours = new HashSet<>(List.of("Day", "CPS", "Potion Effects", "Coordinates", "Scoreboard", top));
        // Only the HUDs of the layout, so nothing else docks against them by chance.
        for (HudElement element : HudManager.getInstance().getElements()) {
            Module module = element.getModuleName() == null ? null : ModuleManager.getInstance().getModule(element.getModuleName());
            if (module == null) continue;
            ENABLED_WERE.put(module, module.isEnabled());
            module.setEnabled(ours.contains(module.getName()));
        }
        // Auto Hide would fade the whole Lads HUD out while the QA player stands still.
        Module autohide = ModuleManager.getInstance().getModule("Autohide");
        if (autohide != null) { ENABLED_WERE.put(autohide, autohide.isEnabled()); autohide.setEnabled(false); }
        settings.clearPositions();
        settings.setHudScale(0);
        d.window(1600, 900); // GUI scale 3 needs at least 960 x 720 pixels
        // No chat lines over the HUD (the game rules' names before and after 26.x; the other spelling fails quietly).
        for (String rule : new String[] {"sendCommandFeedback", "send_command_feedback", "logAdminCommands", "log_admin_commands"}) d.command("gamerule " + rule + " false");
        d.command("gamemode creative @a"); // nothing in the QA world hurts the player meanwhile
        d.effect(null, 0);
        d.effect("speed", 1);
        d.effect("haste", 3);
        d.command("scoreboard objectives add ladsqa dummy");
        d.command("scoreboard objectives setdisplay sidebar ladsqa");
        d.command("scoreboard players set Alpha ladsqa 3");
        d.command("scoreboard players set Beta ladsqa 2");
        LOGGER.info("Lads hud layout BEGIN: Scoreboard docked under {}", top);
    }

    /** The layout as a build before 1.9.0 saved it at GUI scale 3: bare x, y positions (HudManager anchors them on the next frame). */
    private static void load(LadsGraphics g) {
        HudSettings settings = HudSettings.getInstance();
        Rect hotbar = HudAnchors.hotbar(g);
        Rect day = editorBox("Day", g), cps = editorBox("CPS", g), potion = editorBox("Potion Effects", g), held = editorBox(top, g), board = editorBox("Scoreboard", g);
        int width = g.getScaledWidth();
        potionRoom = potion.height();
        settings.setPosition("Day", hotbar.right() + 6, hotbar.bottom() - day.height());
        settings.setPosition("CPS", hotbar.x() - 6 - cps.width(), hotbar.bottom() - cps.height());
        settings.setPosition("Potion Effects", 5, 30);
        settings.setPosition("Coordinates", 5, 30 + potion.height() + 2);
        settings.setPosition(top, width - held.width() - 5, 5);
        settings.setPosition("Scoreboard", width - board.width() - 5, 5 + held.height() + 2);
        LOGGER.info("Lads hud layout saved at {}x{} (GUI scale {}): {}", width, g.getScaledHeight(), g.guiScale(), describe(settings.getPositions()));
        expect("loading the layout", "the layout is saved at GUI scale 3", g.guiScale() == 3, "GUI scale " + g.guiScale());
    }

    private static Rect editorBox(String name, LadsGraphics g) {
        for (HudElement element : HudManager.getInstance().getElements())
            if (name.equals(element.getModuleName())) return element.measureBounds(g, true);
        throw new IllegalStateException("no HUD " + name);
    }

    private static void check(String label, int after) {
        afterFrame(label, g -> check(label, g));
        wait = after;
    }

    /** Runs after the next Lads HUD frame; a failure is logged, never thrown into the game's render. */
    private static void afterFrame(String label, java.util.function.Consumer<LadsGraphics> action) {
        HudManager.afterRender = g -> {
            try { action.accept(g); }
            catch (RuntimeException failure) { FAILURES.add(label + ": " + failure); LOGGER.error("Lads hud layout check FAIL: {}", label, failure); }
        };
    }

    /** What every frame must show: Day and CPS on the hotbar's row (or Day on top of it), the docked HUDs under what holds them. */
    private static void check(String label, LadsGraphics g) {
        var hud = HudManager.getInstance();
        Rect hotbar = HudAnchors.hotbar(g), day = hud.lastBounds("Day"), cps = hud.lastBounds("CPS"), potion = hud.lastBounds("Potion Effects"),
            coords = hud.lastBounds("Coordinates"), held = hud.lastBounds(top), board = hud.lastBounds("Scoreboard");
        LOGGER.info("Lads hud layout {}: {}x{} at Lads HUD scale {} ({} vanilla GUI units per Lads unit), hotbar {}, Day {} [{}], CPS {} [{}], Potion Effects {}, Coordinates {} [{}], {} {}, Scoreboard {} [{}]",
            label, g.getScaledWidth(), g.getScaledHeight(), g.guiScale(), 1 / g.hudUnit(), hotbar, day, HudAnchors.holder("Day"), cps, HudAnchors.holder("CPS"),
            potion, coords, HudAnchors.holder("Coordinates"), top, held, board, HudAnchors.holder("Scoreboard"));
        if (onHotbar) expect(label, "Day sits on top of the hotbar, centred", day.bottom() == hotbar.y() && Math.abs(day.x() + day.width() / 2 - (hotbar.x() + hotbar.width() / 2)) <= 1, day + " hotbar " + hotbar);
        else expect(label, "Day is 6 px right of the hotbar, bottoms lined up", day.x() == hotbar.right() + 6 && day.bottom() == hotbar.bottom(), day + " hotbar " + hotbar);
        expect(label, "CPS is 6 px left of the hotbar, bottoms lined up", cps.right() == hotbar.x() - 6 && cps.bottom() == hotbar.bottom(), cps + " hotbar " + hotbar);
        expect(label, "Coordinates stays 2 px under the room Potion Effects had, pushed when the effects need more, never overlapped",
            coords.y() == Math.max(potion.y() + potionRoom, potion.bottom()) + 2 && coords.y() >= potion.bottom(), coords + " potions " + potion + " room " + potionRoom);
        expect(label, "the Scoreboard stays 2 px under " + top + " and never covers it", board.y() == held.bottom() + 2, board + " " + top + " " + held);
    }

    private static void expect(String label, String what, boolean ok, String detail) {
        if (ok) { passed++; LOGGER.info("Lads hud layout check PASS: {}: {}", label, what); }
        else { FAILURES.add(label + ": " + what + " (" + detail + ")"); LOGGER.error("Lads hud layout check FAIL: {}: {} ({})", label, what, detail); }
    }

    /** Presses on Day in the editor and moves it to just above the hotbar's middle (held there), or releases it there. */
    private static void drag(DraggableHudScreen ui, boolean release) {
        if (ui == null) throw new IllegalStateException("the HUD editor is not open");
        Rect day = ui.boundsFor("Day"), hotbar = ui.hotbarBounds();
        int x = hotbar.x() + hotbar.width() / 2 - day.width() / 2 + 1, y = hotbar.y() - day.height() - 1 + 1;
        if (release) { ui.mouseReleased(ui.screenX(x), ui.screenY(y), 0, 0); return; }
        ui.mouseClicked(ui.screenX(day.x() + 1), ui.screenY(day.y() + 1), 0, 0);
        ui.mouseDragged(ui.screenX(x), ui.screenY(y), 0);
    }

    private static void end(Driver d) {
        step = 99;
        HudManager.afterRender = null;
        try {
            d.closeScreen();
            HudSettings settings = HudSettings.getInstance();
            settings.clearPositions();
            settings.getPositions().putAll(POSITIONS_WERE);
            settings.getAnchors().putAll(ANCHORS_WERE);
            settings.replaceGroups(GROUPS_WERE);
            settings.replaceLocked(LOCKED_WERE);
            settings.setHudScale(scaleWas);
            ENABLED_WERE.forEach(Module::setEnabled);
            d.effect(null, 0);
            d.command("scoreboard objectives remove ladsqa");
            for (String rule : new String[] {"sendCommandFeedback", "send_command_feedback", "logAdminCommands", "log_admin_commands"}) d.command("gamerule " + rule + " true");
            d.guiScale(2);
            d.window(0, 0);
        } catch (RuntimeException failure) {
            FAILURES.add("restoring: " + failure);
        }
        if (!FAILURES.isEmpty()) LOGGER.error("Lads hud layout FAILED: {}", String.join(" | ", FAILURES));
        LOGGER.info("Lads hud layout END: {} passed, {} failed", passed, FAILURES.size());
    }

    private static String describe(Map<String, int[]> positions) {
        StringBuilder out = new StringBuilder();
        positions.forEach((name, at) -> out.append(name).append('=').append(at[0]).append(',').append(at[1]).append(' '));
        return out.toString().trim();
    }
}
