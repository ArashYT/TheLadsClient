package com.thelads.core.client.hypixel;

import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import com.thelads.core.config.SliderOption;
import com.thelads.core.config.TextOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Hypixel modules (Hytilities / AutoGG class, all allowed on Hypixel), active only while connected to Hypixel: Auto GG, Auto
 * Friend, Auto Tip, Auto Play Again, Auto Who and Chat Cleanup. Each version feeds it server chat (system messages, not the action
 * bar) and a client tick. Every command it sends goes through one queue, at least 1.5 s after the one before; each trigger fires
 * once (a game end or start once per world, a friend once a minute, a tip once per interval), so nothing can loop.
 */
public final class HypixelMods {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    public static final String AUTO_GG = "Auto GG", AUTO_FRIEND = "Auto Friend", AUTO_TIP = "Auto Tip", PLAY_AGAIN = "Auto Play Again",
        AUTO_WHO = "Auto Who", CLEANUP = "Chat Cleanup";
    static final long GAP = 1_500_000_000L, SECOND = 1_000_000_000L;
    private static final int MAX_QUEUED = 8;

    /** A command or chat line due at {@code due}; {@code world}: dropped when the world changes before it is sent. */
    private record Queued(long due, String text, boolean world) {}
    private static final List<Queued> QUEUE = new ArrayList<>();
    private static final Map<String, Long> FRIENDS = new HashMap<>();
    private static Object connection, world;
    private static boolean gameOver, started, tipOff;
    private static long lastSent, tipAt;
    private static String lastCommand = "";
    private static String[] location;
    /** QA only: the lines sent (the first 64), in order, and how many server messages were hidden. */
    public static final List<String> SENT = new ArrayList<>();
    public static int hidden;

    private HypixelMods() {}

    /** The modules, for ModuleManager (category Hypixel). */
    public static List<Module> modules() {
        List<Module> modules = new ArrayList<>();
        Module gg = new Module(AUTO_GG, "Hypixel: says gg in all chat when a game ends, in every mode. A second message is optional (empty: none).");
        gg.addOption(new TextOption("Message", "gg"));
        gg.addOption(new SliderOption("Delay seconds", 1, 0, 10, 0.5));
        gg.addOption(new TextOption("Second Message", ""));
        gg.addOption(new SliderOption("Second Message seconds", 2, 0, 10, 0.5));
        gg.setEnabled(true);
        modules.add(gg);
        Module friend = new Module(AUTO_FRIEND, "Hypixel: accepts friend requests as they arrive.");
        friend.setEnabled(true);
        modules.add(friend);
        Module tip = new Module(AUTO_TIP, "Hypixel: tips every active network booster (/tip all) on a timer while you play. Stops for the session if the server "
            + "does not know the command.");
        tip.addOption(new SliderOption("Every minutes", 15, 5, 60, 5));
        tip.setEnabled(true);
        modules.add(tip);
        Module again = new Module(PLAY_AGAIN, "Hypixel: queues the same mode again (/play) when a game ends, after the delay. Asks the server which mode "
            + "you are in (/locraw) when a world loads.");
        again.addOption(new SliderOption("Delay seconds", 3, 0, 15, 1));
        modules.add(again);
        modules.add(new Module(AUTO_WHO, "Hypixel: lists the players in your game (/who) as it starts."));
        Module cleanup = new Module(CLEANUP, "Hypixel: hides lobby spam from chat: rank join messages, Mystery Box and Soul Well finds, gifts, tips, "
            + "boosters, store ads, Watchdog announcements and Karma; optionally reward lines and other players' ads.");
        for (HypixelChat.Spam kind : HypixelChat.Spam.values()) cleanup.addOption(new BoolOption(kind.option, kind.on));
        cleanup.setEnabled(true);
        modules.add(cleanup);
        return modules;
    }

    private static Module module(String name) { return ModuleManager.getInstance().getModule(name); }
    private static boolean on(String name) { Module m = module(name); return m != null && m.isEnabled(); }
    private static Option option(String module, String name) { Module m = module(module); return m == null ? null : m.getOption(name); }
    private static long seconds(String module, String name, double fallback) {
        Option o = option(module, name);
        return (long) ((o instanceof SliderOption s ? s.getValue() : fallback) * SECOND);
    }
    private static String text(String module, String name) {
        Option o = option(module, name);
        String text = o instanceof TextOption t ? t.getValue().replace('\n', ' ').trim() : "";
        return text.length() > 96 ? text.substring(0, 96) : text;
    }

    /**
     * Each client tick. {@code currentWorld}: the client world (a new one for each Hypixel server or game), {@code current}: the
     * connection (any object unique to it), both null when not playing. Also measures the real ping (RealPing).
     */
    public static void tick(LadsGameBridge game, Object currentWorld, Object current, long now) {
        RealPing.tick(game, current, now);
        if (current != connection) {
            connection = current;
            QUEUE.clear();
            FRIENDS.clear();
            tipOff = false;
            world = null;
            tipAt = now + 30 * SECOND; // the first tip half a minute after joining
        }
        if (current == null || !HypixelSafetyManager.isHypixel()) {
            QUEUE.clear();
            return;
        }
        if (currentWorld != world) {
            world = currentWorld;
            gameOver = started = false;
            location = null;
            QUEUE.removeIf(Queued::world);
            if (currentWorld != null && on(PLAY_AGAIN)) queue("/locraw", now + 2 * SECOND, true);
        }
        if (on(AUTO_TIP) && !tipOff && now - tipAt >= 0) {
            queue("/tip all", now, false);
            tipAt = now + Math.max(5 * 60 * SECOND, seconds(AUTO_TIP, "Every minutes", 15) * 60);
        }
        send(game, now);
    }

    /** Sends the first line that is due, at least {@link #GAP} after the last one. */
    private static void send(LadsGameBridge game, long now) {
        if (lastCommand.length() > 0 && now - lastSent < GAP) return;
        Queued next = null;
        for (Queued q : QUEUE) if (now - q.due() >= 0 && (next == null || q.due() - next.due() < 0)) next = q;
        if (next == null) return;
        QUEUE.remove(next);
        lastSent = now;
        lastCommand = next.text();
        if (SENT.size() < 64) SENT.add(next.text());
        LOGGER.info("Lads Hypixel: sending {}", next.text());
        game.sendChatLine(next.text());
    }

    private static void queue(String text, long due, boolean world) {
        if (QUEUE.size() >= MAX_QUEUED) return;
        for (Queued q : QUEUE) if (q.text().equals(text)) return;
        QUEUE.add(new Queued(due, text, world));
    }

    /**
     * A server chat message (not the action bar) as it arrives, formatting codes allowed; true when Chat Cleanup hides it.
     * Off Hypixel nothing happens.
     */
    public static boolean chat(String message, long now) {
        if (message == null || connection == null || !HypixelSafetyManager.isHypixel()) return false;
        if (lastCommand.equals("/tip all") && now - lastSent < 5 * SECOND && HypixelChat.unknownCommand(message)) {
            tipOff = true;
            LOGGER.info("Lads Hypixel: the server does not know /tip, Auto Tip is off until you reconnect");
        }
        String[] where = HypixelChat.location(message);
        if (where != null && lastCommand.equals("/locraw") && now - lastSent < 5 * SECOND) {
            location = where;
            hidden++;
            return true; // our own question's answer
        }
        if (!gameOver && HypixelChat.gameEnd(message)) {
            gameOver = true;
            gameEnded(now);
        }
        if (!started && HypixelChat.gameStart(message)) {
            started = true;
            if (on(AUTO_WHO)) queue("/who", now + 2 * SECOND, true);
        }
        String friend = HypixelChat.friendRequest(message);
        if (friend != null && on(AUTO_FRIEND)) {
            FRIENDS.values().removeIf(at -> now - at > 60 * SECOND);
            if (FRIENDS.putIfAbsent(friend.toLowerCase(java.util.Locale.ROOT), now) == null) queue("/friend accept " + friend, now, false);
        }
        HypixelChat.Spam spam = on(CLEANUP) ? HypixelChat.spam(message) : null;
        boolean hide = spam != null && option(CLEANUP, spam.option) instanceof BoolOption b && b.get();
        if (hide) hidden++;
        return hide;
    }

    private static void gameEnded(long now) {
        if (on(AUTO_GG)) {
            long first = now + seconds(AUTO_GG, "Delay seconds", 1);
            String message = text(AUTO_GG, "Message"), second = text(AUTO_GG, "Second Message");
            if (!message.isEmpty()) queue("/ac " + message, first, true);
            if (!second.isEmpty()) queue("/ac " + second, first + seconds(AUTO_GG, "Second Message seconds", 2), true);
        }
        String play = location == null ? null : HypixelChat.playCommand(location[0], location[1]);
        if (play != null && on(PLAY_AGAIN)) queue(play, now + seconds(PLAY_AGAIN, "Delay seconds", 3), true);
    }

    /** QA: what the last /locraw said ({gametype, mode}), or null. */
    public static String[] location() { return location; }
    /** QA: Auto Tip stopped for this session (the server said it has no /tip). */
    public static boolean tipStopped() { return tipOff; }
    /** QA: lines waiting in the queue. */
    public static int queued() { return QUEUE.size(); }
    /** QA: the next Auto Tip is due now. */
    public static void tipNow(long now) { tipAt = now; }
    static void resetForTest() {
        QUEUE.clear(); FRIENDS.clear(); SENT.clear(); connection = world = null; gameOver = started = tipOff = false;
        lastSent = 0; lastCommand = ""; location = null;
    }
}
