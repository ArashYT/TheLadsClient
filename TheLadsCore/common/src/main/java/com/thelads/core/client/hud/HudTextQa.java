package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.gui.DraggableHudScreen;
import com.thelads.core.client.hud.HudGroupLayout.Rect;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only, the same on every version (the HUD layout probe's driver runs it instead of HudLayoutQa when LADS_QA_HUDTEXT=1 or
 * -Dthelads.qaHudText=true): the 1.9.1 HUD text sequence. Photographs the Lads HUD with the global text shadow On and Off,
 * Coordinates with the direction on its right (vertical facing NW, horizontal with the biome facing E, then switched off), the HUD editor with
 * Keystrokes selected between other HUDs placed flush against its edges, and chat in survival with armor on (Above Armor Bar on,
 * off, open and closed, and in creative). Frames go to screenshots/text-*.png; lines "Lads hud layout frame ..." and a closing "Lads hud layout END:
 * N passed, M failed" (the HUD layout probe's markers, so the harness waits for it the same way).
 */
public final class HudTextQa {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final List<String> SHOWN = List.of("FPS", "Coordinates", "Biome", "CPS", "Keystrokes", "Direction", "Day", "Time", "Speed",
        "Memory", "Health", "Hunger", "XP", "PingHUD", "Potion Effects", "Scoreboard", "ArmorHUD", "BossBar");
    private static final String[] RULES = {"sendCommandFeedback", "send_command_feedback", "logAdminCommands", "log_admin_commands"};
    private static int step = -1, wait, passed;
    private static final java.util.List<String> FAILURES = new java.util.ArrayList<>();
    private static final Map<String, int[]> POSITIONS_WERE = new HashMap<>();
    private static final Map<String, HudSettings.Anchor> ANCHORS_WERE = new HashMap<>();
    private static final Map<Module, Boolean> ENABLED_WERE = new LinkedHashMap<>();
    private static boolean shadowWas, softWas, verticalWas, biomeWas, directionWas, liftWas;
    private static int scaleWas;

    private HudTextQa() {}

    /** Whether the HUD layout probe runs this sequence instead. */
    public static boolean wanted() {
        return "1".equals(System.getenv("LADS_QA_HUDTEXT")) || Boolean.getBoolean("thelads.qaHudText");
    }

    public static int failed() { return FAILURES.size(); }

    /** Each client tick of the probe; true once the sequence has ended. */
    public static boolean tick(HudLayoutQa.Driver d) {
        try {
            if (step == -1) { begin(d); step = -2; wait = 40; return false; }
            if (wait > 0) { wait--; return false; }
            if (step == -2) { d.guiScale(2); step = 0; wait = 40; return false; }
            HudSettings settings = HudSettings.getInstance();
            switch (step++) {
                case 0 -> { settings.setTextShadow(true); settings.setSoftShadow(false); wait = 20; }
                case 1 -> shot(d, "text-1-shadow-on");
                case 2 -> { settings.setTextShadow(false); wait = 20; }
                case 3 -> shot(d, "text-2-shadow-off");
                case 4 -> { settings.setTextShadow(true); d.command("tp @a ~ ~ ~ 135 0"); wait = 20; }
                case 5 -> shot(d, "text-3-coordinates-vertical-facing-nw");
                case 6 -> { option("Coordinates", "Vertical", false); option("Coordinates", "Show Biome", true); d.command("tp @a ~ ~ ~ 270 0"); wait = 20; }
                case 7 -> shot(d, "text-4-coordinates-horizontal-facing-e");
                case 8 -> { option("Coordinates", "Vertical", true); option("Coordinates", "Show Biome", false); option("Coordinates", "Show Direction", false); wait = 20; }
                case 9 -> shot(d, "text-5-coordinates-direction-off");
                case 10 -> { option("Coordinates", "Show Direction", true); afterFrame("placing", HudTextQa::place); wait = 20; }
                case 11 -> { d.openEditor(); wait = 50; }
                case 12 -> { select(d.editor()); wait = 10; }
                case 13 -> shot(d, "text-6-editor-keystrokes-selected");
                case 14 -> { d.closeScreen(); wait = 20; }
                case 15 -> { survival(d); d.guiScale(3); wait = 60; }
                case 16 -> { chat(d); wait = 30; }
                case 17 -> shot(d, "text-7-chat-above-armor-on");
                case 18 -> { d.chat(true); wait = 20; }
                case 19 -> shot(d, "text-8-chat-open-above-armor-on");
                case 20 -> { d.chat(false); option("Chat", "Above Armor Bar", false); chat(d); wait = 30; }
                case 21 -> shot(d, "text-9-chat-above-armor-off");
                case 22 -> { d.chat(true); wait = 20; }
                case 23 -> shot(d, "text-10-chat-open-above-armor-off");
                case 24 -> { d.chat(false); option("Chat", "Above Armor Bar", true); d.command("gamemode creative @a"); chat(d); wait = 30; }
                case 25 -> shot(d, "text-11-chat-creative");
                default -> { end(d); return true; }
            }
        } catch (RuntimeException failure) {
            FAILURES.add("step " + step + ": " + failure);
            LOGGER.error("Lads hud layout FAILED at text step {}", step, failure);
            end(d);
            return true;
        }
        return false;
    }

    private static void shot(HudLayoutQa.Driver d, String name) {
        d.shot(name);
        LOGGER.info("Lads hud layout frame {}", name);
        passed++;
        wait = 10;
    }

    private static void option(String module, String name, boolean value) {
        if (!(ModuleManager.getInstance().getModule(module).getOption(name) instanceof BoolOption option)) throw new IllegalStateException("no option " + module + "/" + name);
        option.set(value);
    }

    private static boolean option(String module, String name) {
        return ModuleManager.getInstance().getModule(module).getOption(name) instanceof BoolOption option && option.get();
    }

    private static void begin(HudLayoutQa.Driver d) {
        HudSettings settings = HudSettings.getInstance();
        POSITIONS_WERE.putAll(settings.getPositions());
        ANCHORS_WERE.putAll(settings.getAnchors());
        scaleWas = settings.getHudScale();
        shadowWas = settings.isTextShadow();
        softWas = settings.isSoftShadow();
        verticalWas = option("Coordinates", "Vertical");
        biomeWas = option("Coordinates", "Show Biome");
        directionWas = option("Coordinates", "Show Direction");
        liftWas = option("Chat", "Above Armor Bar");
        for (HudElement element : HudManager.getInstance().getElements()) {
            Module module = element.getModuleName() == null ? null : ModuleManager.getInstance().getModule(element.getModuleName());
            if (module == null) continue;
            ENABLED_WERE.put(module, module.isEnabled());
            module.setEnabled(SHOWN.contains(module.getName()));
        }
        Module autohide = ModuleManager.getInstance().getModule("Autohide");
        if (autohide != null) { ENABLED_WERE.put(autohide, autohide.isEnabled()); autohide.setEnabled(false); }
        settings.clearPositions();
        settings.getAnchors().clear();
        settings.setHudScale(0);
        option("Coordinates", "Show Biome", false);
        option("Coordinates", "Show Direction", true);
        option("Chat", "Above Armor Bar", true);
        d.window(1600, 900);
        for (String rule : RULES) d.command("gamerule " + rule + " false");
        d.command("gamemode creative @a");
        d.effect(null, 0);
        d.effect("speed", 1);
        d.effect("haste", 3);
        d.command("scoreboard objectives add ladsqa dummy");
        d.command("scoreboard objectives setdisplay sidebar ladsqa");
        d.command("scoreboard players set Alpha ladsqa 3");
        d.command("scoreboard players set Beta ladsqa 2");
        // Boss bars exist from 1.9 (the 1.8.9 command fails quietly).
        d.command("bossbar add lads:qa {\"text\":\"Lads QA boss\"}");
        d.command("bossbar set lads:qa players @a");
        d.command("bossbar set lads:qa value 60");
        LOGGER.info("Lads hud layout BEGIN: text sequence");
    }

    /** Keystrokes with CPS flush against its right edge and Day flush under it, so the editor's boxes show the edges meet. */
    private static void place(LadsGraphics g) {
        HudSettings settings = HudSettings.getInstance();
        HudElement keys = element("Keystrokes");
        Rect box = keys.measureBounds(g, true);
        int x = g.getScaledWidth() / 2 - box.width(), y = g.getScaledHeight() / 2 - box.height() / 2;
        settings.setPosition("Keystrokes", x, y);
        Rect cps = element("CPS").measureBounds(g, true), day = element("Day").measureBounds(g, true);
        settings.setPosition("CPS", x + box.width(), y);
        settings.setPosition("Day", x, y + box.height());
        LOGGER.info("Lads hud layout text: Keystrokes {} (x {}, y {}), CPS {} flush right of it, Day {} flush under it", box, x, y, cps, day);
    }

    private static HudElement element(String name) {
        for (HudElement element : HudManager.getInstance().getElements()) if (name.equals(element.getModuleName())) return element;
        throw new IllegalStateException("no HUD " + name);
    }

    /** Presses and releases on Keystrokes in the editor, so its box is the selected one. */
    private static void select(DraggableHudScreen ui) {
        if (ui == null) throw new IllegalStateException("the HUD editor is not open");
        Rect keys = ui.boundsFor("Keystrokes");
        double x = ui.screenX(keys.x() + keys.width() / 2.0), y = ui.screenY(keys.y() + keys.height() / 2.0);
        ui.mouseClicked(x, y, 0, 0);
        ui.mouseReleased(x, y, 0, 0);
        LOGGER.info("Lads hud layout text: Keystrokes selected {}", ui.selectedNames());
    }

    /** Survival with a full set of iron armor, so the heart row and the armor row above it show (both command spellings; one fails quietly). */
    private static void survival(HudLayoutQa.Driver d) {
        d.command("gamemode survival @a");
        String[][] pieces = {{"head", "helmet"}, {"chest", "chestplate"}, {"legs", "leggings"}, {"feet", "boots"}};
        for (String[] piece : pieces) {
            d.command("item replace entity @a armor." + piece[0] + " with minecraft:iron_" + piece[1]);
            d.command("replaceitem entity @a slot.armor." + piece[0] + " minecraft:iron_" + piece[1]);
        }
    }

    /** A screenful of short lines, so the newest ones end where the chat's bottom is. */
    private static void chat(HudLayoutQa.Driver d) {
        for (int i = 1; i <= 8; i++) d.command("tellraw @a {\"text\":\"Lads QA chat line " + i + " - the newest line is the lowest\"}");
    }

    private static void afterFrame(String label, java.util.function.Consumer<LadsGraphics> action) {
        HudManager.afterRender = g -> {
            try { action.accept(g); }
            catch (RuntimeException failure) { FAILURES.add(label + ": " + failure); LOGGER.error("Lads hud layout check FAIL: text: {}", label, failure); }
        };
    }

    private static void end(HudLayoutQa.Driver d) {
        step = 99;
        HudManager.afterRender = null;
        try {
            d.closeScreen();
            d.chat(false);
            HudSettings settings = HudSettings.getInstance();
            settings.clearPositions();
            settings.getPositions().putAll(POSITIONS_WERE);
            settings.getAnchors().putAll(ANCHORS_WERE);
            settings.setHudScale(scaleWas);
            settings.setTextShadow(shadowWas);
            settings.setSoftShadow(softWas);
            option("Coordinates", "Vertical", verticalWas);
            option("Coordinates", "Show Biome", biomeWas);
            option("Coordinates", "Show Direction", directionWas);
            option("Chat", "Above Armor Bar", liftWas);
            ENABLED_WERE.forEach(Module::setEnabled);
            d.command("scoreboard objectives remove ladsqa");
            d.command("bossbar remove lads:qa");
            d.command("gamemode creative @a");
            d.effect(null, 0);
            for (String rule : RULES) d.command("gamerule " + rule + " true");
            d.guiScale(2);
            d.window(0, 0);
        } catch (RuntimeException failure) {
            FAILURES.add("restoring: " + failure);
        }
        if (!FAILURES.isEmpty()) LOGGER.error("Lads hud layout FAILED: {}", String.join(" | ", FAILURES));
        LOGGER.info("Lads hud layout END: {} passed, {} failed", passed, FAILURES.size());
    }
}
