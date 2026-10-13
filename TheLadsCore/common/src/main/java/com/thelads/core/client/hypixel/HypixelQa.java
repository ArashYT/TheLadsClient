package com.thelads.core.client.hypixel;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.client.gui.LadsSettingsScreen;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only: the Hypixel modules and the real ping on every version, in the singleplayer QA world (never a public server), one
 * routine driven each client tick by the version (1.8.9 CoreProbe "hypixel", 1.20.1 script verb "hypixel", 26.3 auto-world
 * capture). The world counts as Hypixel for the run (HypixelSafetyManager.qaOverride); Hypixel's messages are reproduced with
 * /tellraw; the commands the modules send go to the integrated server, which does not know them (so Auto Tip's "unknown command"
 * stop is real too). The Mod API ping is answered by the integrated server (1.20.1, 26.3) or by a pong handed to the version's
 * own receive path (1.8.9). Photographs the ping in the HUD and tab list, the chat, and the Hypixel settings; modules and options
 * are put back. Logs "Lads hypixel capture END: n passed, 0 failed" (or FAILED).
 */
public final class HypixelQa {
    /** What the routine needs from a game version. */
    public interface Game {
        /** A command as the player, on the integrated server (no leading "/"). */
        void command(String command);
        /** True when the integrated server answers hypixel:ping itself; else {@link #pong} hands it the answer. */
        boolean serverAnswersPings();
        /** The Mod API's answer, through the version's own receive path. */
        void pong(byte[] payload);
        void holdTab(boolean down);
        /** Opens the chat screen (the history shows), or closes any screen. */
        void openChat();
        /** The Lads settings (at that module's page, or the catalog for null); returns the shared menu it shows. */
        LadsSettingsScreen openSettings(String module);
        void closeScreen();
        void screenshot(String name) throws Exception;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final String[] MODULES = {HypixelMods.HYPIXEL_MODS, "PingHUD", "PingView"};
    private final List<String> failures = new ArrayList<>();
    private final Map<Module, Boolean> enabled = new LinkedHashMap<>();
    private final Map<Option, JsonElement> options = new LinkedHashMap<>();
    private LadsSettingsScreen menu;
    private int step, wait, passed, patience, hiddenBefore;

    public List<String> failures() { return failures; }

    private static Module module(String name) { return ModuleManager.getInstance().getModule(name); }

    /** A line as Hypixel sends it, shown to the player with /tellraw (spaces and § codes kept). */
    private void say(Game game, String text) {
        JsonObject json = new JsonObject();
        json.addProperty("text", text);
        game.command("tellraw @a " + json); // @a: 1.8.9 has no @s
    }

    private boolean sent(String line) { return HypixelMods.SENT.contains(line); }

    /** Waits (up to 10 s) for the condition; then checks it. True to go on. */
    private boolean until(boolean ok, String what) {
        if (!ok && ++patience < 200) { step--; return false; }
        patience = 0;
        check(ok, what);
        return true;
    }

    /** One client tick in the world; true once finished. */
    public boolean tick(Game game) throws Exception {
        if (wait > 0) { wait--; return false; }
        switch (step++) {
            case 0 -> {
                LOGGER.info("Lads hypixel capture BEGIN: the QA world counts as Hypixel");
                for (String name : MODULES) {
                    Module m = module(name);
                    enabled.put(m, m.isEnabled());
                    for (Option o : m.getOptions()) options.put(o, o.save().deepCopy());
                    m.getOptions().forEach(Option::reset);
                    m.setEnabled(true);
                }
                Module hyp = module(HypixelMods.HYPIXEL_MODS);
                for (String feature : List.of(HypixelMods.AUTO_GG, HypixelMods.AUTO_FRIEND, HypixelMods.AUTO_TIP, HypixelMods.PLAY_AGAIN, HypixelMods.AUTO_WHO, HypixelMods.CLEANUP)) {
                    Option o = hyp.getOption(feature);
                    if (o instanceof com.thelads.core.config.BoolOption b) b.set(true);
                }
                hyp.getOption(HypixelMods.AUTO_GG + ": Delay seconds").load(new JsonPrimitive(0.5));
                hyp.getOption(HypixelMods.AUTO_GG + ": Second Message").load(new JsonPrimitive("good game"));
                hyp.getOption(HypixelMods.PLAY_AGAIN + ": Delay seconds").load(new JsonPrimitive(5)); // after both gg lines
                HypixelMods.SENT.clear();
                hiddenBefore = HypixelMods.hidden;
                game.command("scoreboard objectives add ladsqa dummy");
                game.command("scoreboard objectives setdisplay list ladsqa"); // a lone player's tab list shows only with one
                HypixelSafetyManager.qaOverride = true;
            }
            case 1 -> { // a new "Hypixel" world: Auto Play Again asks where it is
                if (!until(sent("/locraw"), "Auto Play Again sent /locraw for the new world " + HypixelMods.SENT)) break;
                say(game, "{\"server\":\"mini123A\",\"gametype\":\"BEDWARS\",\"mode\":\"BEDWARS_EIGHT_ONE\",\"map\":\"Lighthouse\"}");
            }
            case 2 -> {
                if (!until(HypixelMods.location() != null, "the /locraw answer was read and hidden: "
                    + (HypixelMods.location() == null ? "none" : String.join(", ", HypixelMods.location())))) break;
                check(HypixelMods.hidden > hiddenBefore, "the /locraw answer is not in chat");
            }
            case 3 -> { // the real ping
                if (!game.serverAnswersPings()) {
                    long age = RealPing.waiting(System.nanoTime()); // a fresh one: the answer's 1-tick delay is then the round trip
                    if (!until(age >= 0 && age < 150_000_000L, "a Mod API ping went out (hypixel:ping)")) break;
                    wait = 1;
                }
            }
            case 4 -> {
                if (!game.serverAnswersPings()) game.pong(new byte[] {1, 1, 4, 'p', 'o', 'n', 'g'});
            }
            case 5 -> {
                int own = RealPing.own(1);
                if (!until(RealPing.fakeListing() && own != RealPing.NONE, "real ping measured over the Mod API: " + own + " ms; others "
                    + (RealPing.row(1, false) == RealPing.NONE ? "-" : String.valueOf(RealPing.row(1, false))) + " (no number)")) break;
                check(RealPing.row(1, false) == RealPing.NONE, "another player's listed 1 ms shows no number");
                check(own >= 30, "the round trip includes the answer's 37 ms wait (1.8.9: one tick): " + own + " ms");
                game.holdTab(true);
                wait = 10;
            }
            case 6 -> { game.screenshot("hypixel-ping-tab-hud"); wait = 5; }
            case 7 -> {
                game.holdTab(false);
                say(game, "[MVP+] Steve joined the lobby!");
                say(game, "[VIP] Steve: hello, this chat stays");
                say(game, "+5 Karma!");
                say(game, "They have gifted 15 ranks so far!");
                say(game, "-----------------------------------------------------\nFriend request from [VIP] Steve_2\n[ACCEPT] - [DENY] - [IGNORE]\n"
                    + "-----------------------------------------------------");
                wait = 10;
            }
            case 8 -> {
                if (!until(sent("/friend accept Steve_2"), "Auto Friend accepted Steve_2 " + HypixelMods.SENT)) break;
                check(HypixelMods.hidden - hiddenBefore >= 4, "Chat Cleanup hid the lobby join, Karma and gift lines (" + (HypixelMods.hidden - hiddenBefore) + " hidden incl. /locraw)");
                say(game, "The game starts in 1 second!");
            }
            case 9 -> {
                if (!until(sent("/who"), "Auto Who sent /who as the game started " + HypixelMods.SENT)) break;
                say(game, "                       1st Killer - [MVP+] Steve - 7");
                say(game, "                       2nd Killer - Alex - 3");
            }
            case 10 -> {
                if (!until(sent("/play bedwars_eight_one"), "Auto GG then Auto Play Again: " + HypixelMods.SENT)) break;
                int gg = HypixelMods.SENT.indexOf("/ac gg"), second = HypixelMods.SENT.indexOf("/ac good game"), play = HypixelMods.SENT.indexOf("/play bedwars_eight_one");
                check(gg >= 0 && second > gg && play > second, "once each, in order: " + HypixelMods.SENT);
                HypixelMods.tipNow(System.nanoTime());
            }
            case 11 -> {
                if (!until(sent("/tip all") && HypixelMods.tipStopped(), "Auto Tip sent /tip all and stopped when the server said it has no /tip "
                    + HypixelMods.SENT)) break;
                int count = HypixelMods.SENT.size();
                check(new java.util.HashSet<>(HypixelMods.SENT).size() == count, "no command was sent twice: " + HypixelMods.SENT);
                game.openChat();
                wait = 10;
            }
            case 12 -> { game.screenshot("hypixel-chat"); wait = 5; }
            case 13 -> {
                menu = game.openSettings(null);
                wait = 10;
            }
            case 14 -> {
                LadsSettingsScreen.Rect tab = menu.controlBounds("category:Hypixel");
                check(tab != null, "the settings have a Hypixel category " + menu.controlIds());
                if (tab != null) {
                    menu.mouseClicked(tab.x() + 4, tab.y() + 4, 0);
                    menu.mouseReleased(tab.x() + 4, tab.y() + 4, 0);
                }
                wait = 10;
            }
            case 15 -> {
                check(menu.visibleModuleNames().equals(List.of(HypixelMods.HYPIXEL_MODS)), "the Hypixel category lists the one module " + menu.visibleModuleNames());
                game.screenshot("hypixel-settings");
                wait = 5;
            }
            case 16 -> { menu = game.openSettings(HypixelMods.HYPIXEL_MODS); wait = 10; }
            case 17 -> { game.screenshot("hypixel-mods"); wait = 5; }
            case 18 -> { game.screenshot("hypixel-auto-gg"); wait = 5; }
            case 19 -> { game.screenshot("hypixel-chat-cleanup"); wait = 5; }
            default -> {
                game.closeScreen();
                HypixelSafetyManager.qaOverride = false;
                game.command("scoreboard objectives remove ladsqa");
                for (Map.Entry<Option, JsonElement> o : options.entrySet()) o.getKey().load(o.getValue());
                for (Map.Entry<Module, Boolean> m : enabled.entrySet()) m.getKey().setEnabled(m.getValue());
                if (failures.isEmpty()) LOGGER.info("Lads hypixel capture END: {} passed, 0 failed", passed);
                else LOGGER.error("Lads hypixel capture FAILED: {} passed, {} failed: {}", passed, failures.size(), failures);
                return true;
            }
        }
        return false;
    }

    private void check(boolean ok, String what) {
        if (ok) { passed++; LOGGER.info("Lads hypixel QA ok: {}", what); }
        else { failures.add(what); LOGGER.error("Lads hypixel QA FAILED: {}", what); }
    }
}
