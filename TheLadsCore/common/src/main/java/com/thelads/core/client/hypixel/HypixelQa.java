package com.thelads.core.client.hypixel;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.client.OldAnimations;
import com.thelads.core.modules.OldAnimationsModule;
import com.thelads.core.modules.StackRefillModule;
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
        /** Survival, hotbar slot 0 selected with 1 cobblestone and 16 more in the first inventory slot (Stack Refill); false where not wired. */
        default boolean stockRefill() { return false; }
        /** Empties the selected hotbar slot on the integrated server, as placing its last block does. */
        default void emptyHand() {}
        /** The selected hand as "item xN", "" when empty. */
        default String held() { return ""; }
        /** QA: the client's announced channels at this moment (QuietChannelsQa). */
        default void channels(String when) {}
        /** Xaero's own minimap switch, or null where Lads does not drive it (26.3 skips the draw instead). */
        default Boolean xaeroShown() { return null; }
        /** Flips Xaero's own minimap switch as its key does; false where not wired. */
        default boolean flipXaero() { return false; }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final String[] MODULES = {HypixelMods.HYPIXEL_MODS, "PingHUD", "PingView", "Coordinates"};
    private final List<String> failures = new ArrayList<>();
    private final Map<Module, Boolean> enabled = new LinkedHashMap<>();
    private final Map<Option, JsonElement> options = new LinkedHashMap<>();
    private LadsSettingsScreen menu;
    private int step, wait, passed, patience, hiddenBefore;
    private boolean stocked;
    /** The steps before the closing one (case 0 to 41), for the QA bar; barStep is the one it shows. */
    private static final int STEPS = 42;
    private int barStep = -1;

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
        if (step == 0) com.thelads.core.client.qa.QaProgress.begin("Hypixel", STEPS);
        if (step != barStep && step < STEPS) com.thelads.core.client.qa.QaProgress.step("step " + ((barStep = step) + 1));
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
                game.channels("hypixel start (before the first ping)");
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
                game.channels("hypixel after the ping");
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
                game.closeScreen();
                Module hyp = module(HypixelMods.HYPIXEL_MODS);
                hyp.getOption(HypixelMods.PLAY_AGAIN + ": " + HypixelMods.REPLAY_WHEN).load(new JsonPrimitive("After death"));
                hyp.getOption(HypixelMods.PLAY_AGAIN + ": " + HypixelMods.WAIT_PARTY).load(new JsonPrimitive(true));
                hyp.getOption(HypixelMods.PLAY_AGAIN + ": Delay seconds").load(new JsonPrimitive(0.1));
                HypixelMods.rearmForQa();
                say(game, "You have joined Teammate_Bob's party!");
                wait = 5;
            }
            case 14 -> {
                say(game, "The game starts in 1 second!");
                wait = 5;
            }
            case 15 -> {
                say(game, "You died! You will respawn in 5 seconds!");
                wait = 5;
            }
            case 16 -> {
                check(!HypixelMods.isSelfOut(), "Bed Wars respawn death does not count as self out");
                say(game, "You died! Want to play again? Click here to play again!");
                wait = 10;
            }
            case 17 -> {
                check(HypixelMods.isSelfOut(), "Bed Wars final death / Click here to play again counts as self out");
                check(!sent("/play bedwars_eight_one"), "party of 2: you die -> no /play while teammate alive " + HypixelMods.SENT);
                say(game, "Teammate_Bob was killed by Enemy. FINAL KILL!");
            }
            case 18 -> {
                if (!until(sent("/play bedwars_eight_one"), "party of 2: teammate final-killed -> /play sent " + HypixelMods.SENT)) break;
                Module hyp = module(HypixelMods.HYPIXEL_MODS);
                hyp.getOption(HypixelMods.PLAY_AGAIN + ": " + HypixelMods.REPLAY_WHEN).load(new JsonPrimitive("When game ends"));
                HypixelMods.rearmForQa();
                say(game, "The game starts in 1 second!");
                wait = 5;
            }
            case 19 -> {
                say(game, "You died! Want to play again? Click here to play again!");
                wait = 10;
            }
            case 20 -> {
                check(!sent("/play bedwars_eight_one"), "'When game ends': death sends nothing " + HypixelMods.SENT);
                say(game, "                       1st Killer - [MVP+] Steve - 7");
                say(game, "                       2nd Killer - Alex - 3");
            }
            case 21 -> {
                if (!until(sent("/play bedwars_eight_one"), "'When game ends': game over sends /play " + HypixelMods.SENT)) break;
                Module hyp = module(HypixelMods.HYPIXEL_MODS);
                hyp.getOption(HypixelMods.PLAY_AGAIN + ": Delay seconds").load(new JsonPrimitive(3));
            }
            case 22 -> {
                menu = game.openSettings(null);
                wait = 10;
            }
            case 23 -> {
                LadsSettingsScreen.Rect tab = menu.controlBounds("category:Hypixel");
                check(tab != null, "the settings have a Hypixel category " + menu.controlIds());
                if (tab != null) {
                    menu.mouseClicked(tab.x() + 4, tab.y() + 4, 0);
                    menu.mouseReleased(tab.x() + 4, tab.y() + 4, 0);
                }
                wait = 10;
            }
            case 24 -> {
                // HYPIXEL Mods and, since 1.9.14, Quickplay: the two built-in modules of the Hypixel category on every version.
                check(menu.visibleModuleNames().equals(List.of(HypixelMods.HYPIXEL_MODS, com.thelads.core.client.hypixel.quickplay.QuickplayModule.NAME)),
                    "the Hypixel category lists its two modules, HYPIXEL Mods and Quickplay " + menu.visibleModuleNames());
                game.screenshot("hypixel-settings");
                wait = 5;
            }
            case 25 -> { menu = game.openSettings(HypixelMods.HYPIXEL_MODS); patience = 0; wait = 10; }
            case 26 -> {
                check(!menu.currentDetailHasPreview(), "Hypixel Mods module settings has NO preview panel");
                LadsSettingsScreen.Rect first = menu.controlBounds("option:" + HypixelMods.AUTO_GG);
                LadsSettingsScreen.Rect second = menu.controlBounds("option:" + HypixelMods.AUTO_GG + ": Message");
                check(first != null && second != null && second.x() > first.x() + 100, "Hypixel Mods settings in two columns: first=" + (first != null ? first.x() : -1) + ", second=" + (second != null ? second.x() : -1));
                game.screenshot("hypixel-mods");
                patience = 0;
                wait = 5;
            }
            case 27 -> {
                if (menu.controlBounds("option:" + HypixelMods.PLAY_AGAIN + ": " + HypixelMods.WAIT_PARTY) != null) {
                    patience = 0;
                    check(menu.controlBounds("option:" + HypixelMods.PLAY_AGAIN + ": " + HypixelMods.REPLAY_WHEN) != null,
                        "Replay when option rendered in settings");
                    check(menu.controlBounds("option:" + HypixelMods.PLAY_AGAIN + ": " + HypixelMods.WAIT_PARTY) != null,
                        "Wait for party option rendered in settings");
                    if (menu.getScrollOffset() == 0) menu.scrollBy(100);
                    int scrolled = menu.getScrollOffset();
                    check(scrolled > 0, "scrolled Hypixel Mods settings: " + scrolled + " > 0");
                    wait = 5;
                } else if (++patience < 30) {
                    menu.scrollBy(43);
                    step--;
                    wait = 2;
                } else {
                    patience = 0;
                    check(false, "Wait for party option rendered in settings " + menu.controlIds());
                    wait = 5;
                }
            }
            case 28 -> {
                check(menu.getScrollOffset() > 0, "Hypixel Mods settings offset is scrolled: " + menu.getScrollOffset());
                game.screenshot("hypixel-mods-scrolled");
                wait = 5;
            }
            case 29 -> {
                menu = game.openSettings("Coordinates");
                wait = 10;
            }
            case 30 -> {
                check(menu.currentDetailHasPreview(), "Coordinates module settings HAS live HUD preview panel");
                game.screenshot("hud-preview-settings");
                wait = 5;
            }
            case 31 -> {
                int initial = menu.getScrollOffset();
                boolean downHandled = menu.keyPressed(264, 0); // Down arrow
                int afterDown = menu.getScrollOffset();
                check(downHandled && afterDown == initial + 28, "down arrow key scrolled settings by 28px: " + afterDown);

                boolean upHandled = menu.keyPressed(265, 0); // Up arrow
                int afterUp = menu.getScrollOffset();
                check(upHandled && afterUp == initial, "up arrow key scrolled settings back to initial: " + afterUp);

                LadsSettingsScreen.Rect track = menu.scrollbarTrackBounds();
                LadsSettingsScreen.Rect thumb = menu.scrollbarThumbBounds();
                check(track != null && thumb != null, "scrollbar track and thumb bounds exist: " + track + ", " + thumb);
                if (track != null && thumb != null) {
                    menu.mouseClicked(thumb.x() + 2, thumb.y() + thumb.height() / 2, 0);
                    check(menu.isDraggingScrollbar(), "scrollbar thumb click starts dragging");
                    menu.mouseDragged(thumb.x() + 2, thumb.y() + 40, 0, 0, 40);
                    int afterDrag = menu.getScrollOffset();
                    check(afterDrag > 0, "dragging scrollbar thumb updated scrollOffset: " + afterDrag);
                    menu.mouseReleased(thumb.x() + 2, thumb.y() + 40, 0);
                    check(!menu.isDraggingScrollbar(), "scrollbar thumb released");

                    menu.scrollBy(-10000);
                    menu.mouseClicked(track.x() + 2, track.y() + track.height() - 2, 0);
                    int afterPageDown = menu.getScrollOffset();
                    check(afterPageDown > 0, "clicking scrollbar track below thumb paged down: " + afterPageDown);
                }
                wait = 5;
            }
            case 32 -> {
                check(menu.getScrollOffset() > 0, "Coordinates settings offset is scrolled: " + menu.getScrollOffset());
                game.screenshot("scrollbar-scrolled");
                wait = 5;
            }
            // 1.9.12: on a server (the QA world counts as one here) Stack Refill and 1.7 use-while-mining are singleplayer only and the
            // minimaps are off where networks ban them; then back in singleplayer with each switch as it was.
            case 33 -> {
                game.closeScreen();
                HypixelSafetyManager.qaOverride = false; // the switches' own state, read while nothing blocks them
                for (Module m : ModuleManager.getInstance().getModules())
                    if (HypixelSafetyManager.isMinimapModule(m.getName()) || m.getName().equals(StackRefillModule.NAME) || m.getName().equals(OldAnimationsModule.NAME)) {
                        enabled.putIfAbsent(m, m.isEnabled());
                        m.setEnabled(true);
                    }
                HypixelSafetyManager.qaOverride = true;
                Module refill = module(StackRefillModule.NAME);
                check(refill != null && refill.isBlockedByServer() && !refill.isEnabled() && HypixelSafetyManager.getBlockedNotice(refill.getName()).contains("off on this server"),
                    "on a server Stack Refill is off and says the server rules turned it off: " + (refill == null ? "-" : HypixelSafetyManager.getBlockedNotice(refill.getName())));
                check(module(OldAnimationsModule.NAME) instanceof OldAnimationsModule anim && !anim.useWhileMining(OldAnimations.Use.BLOCK) && !anim.useWhileMining(OldAnimations.Use.EAT_DRINK),
                    "on a server a block or eat waits for the mining to stop (1.8)");
                int maps = 0;
                for (Module m : ModuleManager.getInstance().getModules())
                    if (HypixelSafetyManager.isMinimapModule(m.getName())) { maps++; check(m.isBlockedByServer() && !m.isEnabled(), "on a minimap-banned server " + m.getName() + " is off"); }
                check(maps > 0, "a minimap module exists: " + maps);
                stocked = game.stockRefill();
                menu = game.openSettings(null);
                menu.setSearchQuery("Stack");
                wait = 10;
            }
            case 34 -> { game.screenshot("hypixel-sp-only-stackrefill"); wait = 5; }
            case 35 -> {
                game.closeScreen();
                wait = 5; // Stack Refill sees the full hand first
            }
            case 36 -> {
                if (stocked) game.emptyHand();
                if (game.flipXaero()) LOGGER.info("Lads hypixel QA: Xaero's own minimap switch flipped (its key) on the banned server");
                wait = 20;
            }
            case 37 -> {
                if (stocked) check(game.held().isEmpty(), "on a server Stack Refill moved nothing: the emptied hand stays empty '" + game.held() + "'");
                Boolean xaero = game.xaeroShown();
                if (xaero != null) check(!xaero, "on a minimap-banned server Xaero's own minimap stays off, its key included");
                game.screenshot("hypixel-minimap-hidden"); // 26.3 writes the next frame: the override stays on for it
                wait = 5;
            }
            case 38 -> {
                HypixelSafetyManager.qaOverride = false; // singleplayer again
                wait = 20;
            }
            case 39 -> {
                Module refill = module(StackRefillModule.NAME);
                check(refill != null && refill.isEnabled(), "in singleplayer Stack Refill is on again");
                check(module(OldAnimationsModule.NAME) instanceof OldAnimationsModule anim && anim.useWhileMining(OldAnimations.Use.BLOCK),
                    "in singleplayer a block can start while mining again (1.7)");
                for (Module m : ModuleManager.getInstance().getModules())
                    if (HypixelSafetyManager.isMinimapModule(m.getName())) check(m.isEnabled(), "in singleplayer " + m.getName() + " is back on (its switch was not flipped)");
                Boolean xaero = game.xaeroShown();
                if (xaero != null) check(xaero, "in singleplayer Xaero's own minimap is shown again");
                if (stocked) game.stockRefill();
                wait = 10;
            }
            case 40 -> {
                if (stocked) game.emptyHand();
                wait = 20;
            }
            case 41 -> {
                if (stocked) check(game.held().endsWith("x16"), "in singleplayer Stack Refill refilled the emptied hand from the inventory: '" + game.held() + "'");
                game.screenshot("hypixel-minimap-back");
                wait = 5;
            }
            default -> {
                game.closeScreen();
                HypixelSafetyManager.qaOverride = false;
                game.command("scoreboard objectives remove ladsqa");
                for (Map.Entry<Option, JsonElement> o : options.entrySet()) o.getKey().load(o.getValue());
                for (Map.Entry<Module, Boolean> m : enabled.entrySet()) m.getKey().setEnabled(m.getValue());
                com.thelads.core.client.qa.QaProgress.done();
                if (failures.isEmpty()) LOGGER.info("Lads hypixel capture END: {} passed, 0 failed", passed);
                else LOGGER.error("Lads hypixel capture FAILED: {} passed, {} failed: {}", passed, failures.size(), failures);
                return true;
            }
        }
        return false;
    }

    private void check(boolean ok, String what) {
        if (ok) { passed++; LOGGER.info("Lads hypixel QA ok: {}", what); }
        else { failures.add(what); LOGGER.error("Lads hypixel QA FAILED: {}", what); com.thelads.core.client.qa.QaProgress.result(com.thelads.core.client.qa.QaProgress.Result.FAIL); }
    }
}
