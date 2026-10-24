package com.thelads.core.client.hypixel;

import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import com.thelads.core.config.SliderOption;
import com.thelads.core.config.TextOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
        TAB_FILL = "Hypixel tab fill",
        AUTO_WHO = "Auto Who", CLEANUP = "Chat Cleanup";
    public static final String CHAT_EMOTES = "Chat emotes", STREAMER_MODE = "Streamer Mode";
    public static final String AUTO_GL = "Auto GL";
    public static final String LEVEL_HEAD = LevelHead.NAME;
    public static final String HY_STATS = HyStats.NAME;
    public static final String PIT_LAG = CrowdPlayerHider.PIT_LAG;
    public static final String SKYBLOCK_NPC_HIDE = CrowdPlayerHider.SKYBLOCK_NPC_HIDE;
    public static final String OPENED_CHESTS = OpenedChestHighlight.NAME;
    public static final String LOW_BLOCK = LowBlockWarning.NAME;
    public static final String SILENT_LOBBY = SilentLobby.NAME;
    public static final String BW_EXTRAS = BedWarsExtras.NAME;
    public static final String SKYBLOCK_SUITE = SkyBlockSuite.NAME;
    public static final String REPLAY_WHEN = "Replay when", WAIT_PARTY = "Wait for party";
    static final long GAP = 1_500_000_000L, SECOND = 1_000_000_000L;
    private static final int MAX_QUEUED = 8;

    /** A command or chat line due at {@code due}; {@code world}: dropped when the world changes before it is sent. */
    private record Queued(long due, String text, boolean world) {}
    private static final List<Queued> QUEUE = new ArrayList<>();
    private static final Map<String, Long> FRIENDS = new HashMap<>();
    private static final Set<String> PARTY_MEMBERS = new LinkedHashSet<>();
    private static final Set<String> PARTY_ALIVE = new LinkedHashSet<>();
    private static final Set<String> PARTY_ELIMINATED = new LinkedHashSet<>();
    private static Object connection, world;
    private static boolean gameOver, started, tipOff, selfOut, replayed, partyListRequestedThisGame, inParty;
    private static long lastSent, tipAt, tipSent, locrawSent, selfDiedAt, partyListSent, ggDue;
    private static String lastCommand = "";
    private static String[] location;
    private static String mapName, serverName;
    private static boolean mvpPlusPlus;
    private static String qaPlayerName;
    /** QA only: the lines sent (the first 64), in order, and how many server messages were hidden. */
    public static final List<String> SENT = new ArrayList<>();
    public static int hidden;

    private HypixelMods() {}

    public static final String HYPIXEL_MODS = "HYPIXEL Mods";

    /** The grouped module, for ModuleManager (category Hypixel). */
    public static Module module() {
        Module m = new Module(HYPIXEL_MODS, "Hypixel only. Auto GG says gg when a game ends (second message optional); Auto Friend accepts "
            + "every friend request (off by default: anyone could then add you); Auto Tip tips boosters (/tip all) on a timer; Auto Play Again "
            + "queues the same mode (/play) after death or when the game ends, waiting for party by default; Auto Who lists the players (/who) as a game starts; Chat Cleanup hides lobby spam; Chat emotes replaces MVP++ emote shortcodes for non-MVP++; Streamer Mode masks server ID and lobby info.");
        m.addOption(new BoolOption(AUTO_GG, true));
        m.addOption(new TextOption(AUTO_GG + ": Message", "gg"));
        m.addOption(new SliderOption(AUTO_GG + ": Delay seconds", 1, 0, 10, 0.5));
        m.addOption(new TextOption(AUTO_GG + ": Second Message", ""));
        m.addOption(new SliderOption(AUTO_GG + ": Second Message seconds", 2, 0, 10, 0.5));
        m.addOption(new BoolOption(AUTO_FRIEND, false));
        m.addOption(new BoolOption(AUTO_TIP, true));
        m.addOption(new SliderOption(AUTO_TIP + ": Every minutes", 15, 5, 60, 5));
        m.addOption(new BoolOption(PLAY_AGAIN, false));
        m.addOption(new SliderOption(PLAY_AGAIN + ": Delay seconds", 3, 0, 15, 1));
        m.addOption(new DropdownOption(PLAY_AGAIN + ": " + REPLAY_WHEN, 0, "After death", "When game ends"));
        m.addOption(new BoolOption(PLAY_AGAIN + ": " + WAIT_PARTY, true));
        m.addOption(new BoolOption(AUTO_WHO, false));
        m.addOption(new BoolOption(CLEANUP, true));
        for (HypixelChat.Spam kind : HypixelChat.Spam.values()) m.addOption(new BoolOption(CLEANUP + ": " + kind.option, kind.on));
        m.addOption(new BoolOption(CHAT_EMOTES, true));
        m.addOption(new BoolOption(STREAMER_MODE, false));
        m.addOption(new BoolOption(TAB_FILL, true));
        m.addOption(new BoolOption(AUTO_GL, false));
        m.addOption(new TextOption(AUTO_GL + ": Message", "glhf"));
        m.addOption(new SliderOption(AUTO_GL + ": Delay seconds", 0, 0, 5, 0.5));
        m.addOption(new BoolOption(LEVEL_HEAD, false));
        m.addOption(new TextOption(LEVEL_HEAD + ": Prefix", "Level: "));
        m.addOption(new BoolOption(HY_STATS, false));
        m.addOption(new BoolOption(PIT_LAG, true));
        m.addOption(new BoolOption(SKYBLOCK_NPC_HIDE, false));
        m.addOption(new SliderOption(SKYBLOCK_NPC_HIDE + ": Radius", 5, 2, 10, 1));
        m.addOption(new BoolOption(OPENED_CHESTS, false));
        m.addOption(new BoolOption(LOW_BLOCK, false));
        m.addOption(new SliderOption(LOW_BLOCK + ": Threshold", 16, 2, 64, 1));
        m.addOption(new BoolOption(SILENT_LOBBY, true));
        m.addOption(new BoolOption(BW_EXTRAS, true));
        m.addOption(new BoolOption(BW_EXTRAS + ": Hardcore Hearts", true));
        m.addOption(new BoolOption(BW_EXTRAS + ": Bed Alerts", true));
        m.addOption(new BoolOption(SKYBLOCK_SUITE, false));
        m.setEnabled(true);
        return m;
    }

    private static Module registered() { return ModuleManager.getInstance().getModule(HYPIXEL_MODS); }
    /** True when HYPIXEL Mods is enabled by the user AND the client is currently connected to Hypixel. */
    public static boolean isActiveOnCurrentServer() {
        Module m = registered();
        return m != null && m.isEnabled() && HypixelSafetyManager.isHypixel();
    }
    public static boolean on(String feature) {
        if (!isActiveOnCurrentServer()) return false;
        Module m = registered();
        Option o = m.getOption(feature);
        return o instanceof BoolOption b && b.get();
    }
    private static Option option(String feature, String name) {
        Module m = registered();
        return m == null ? null : m.getOption(feature + ": " + name);
    }
    private static long seconds(String feature, String name, double fallback) {
        Option o = option(feature, name);
        return (long) ((o instanceof SliderOption s ? s.getValue() : fallback) * SECOND);
    }
    private static String text(String feature, String name) {
        Option o = option(feature, name);
        String text = o instanceof TextOption t ? t.getValue().replace('\n', ' ').trim() : "";
        return text.length() > 96 ? text.substring(0, 96) : text;
    }

    public static String levelHeadPrefix() { return text(LEVEL_HEAD, "Prefix"); }
    public static double skyblockNpcRadius() {
        Option o = option(SKYBLOCK_NPC_HIDE, "Radius");
        return o instanceof SliderOption s ? s.getValue() : 5.0;
    }
    public static int lowBlockThreshold() {
        Option o = option(LOW_BLOCK, "Threshold");
        return o instanceof SliderOption s ? (int) s.getValue() : 16;
    }
    public static boolean bedWarsHardcoreHearts() {
        Option o = option(BW_EXTRAS, "Hardcore Hearts");
        return o instanceof BoolOption b ? b.get() : true;
    }
    public static boolean bedWarsAlerts() {
        Option o = option(BW_EXTRAS, "Bed Alerts");
        return o instanceof BoolOption b ? b.get() : true;
    }
    public static boolean isInLobby() {
        if (!isActiveOnCurrentServer()) return false;
        if (serverName != null && serverName.toLowerCase(Locale.ROOT).startsWith("lobby")) return true;
        if (location != null && ("MAIN".equalsIgnoreCase(location[0]) || "LOBBY".equalsIgnoreCase(location[0]))) return true;
        return false;
    }

    public static String replayWhenMode() {
        Option o = option(PLAY_AGAIN, REPLAY_WHEN);
        return o instanceof DropdownOption d ? d.getValue() : "After death";
    }

    public static boolean isTabFillEnabled() { return on(TAB_FILL); }
    public static Set<String> knownFriends() { return Collections.unmodifiableSet(FRIENDS.keySet()); }
    public static boolean waitParty() {
        Option o = option(PLAY_AGAIN, WAIT_PARTY);
        return o instanceof BoolOption b ? b.get() : true;
    }

    public static boolean isStreamerMode() { return on(STREAMER_MODE); }
    public static boolean isChatEmotes() { return on(CHAT_EMOTES); }
    public static boolean isMvpPlusPlus() { return mvpPlusPlus; }
    public static void setMvpPlusPlusForTest(boolean mvp) { mvpPlusPlus = mvp; }
    public static String mapName() { return mapName; }
    public static String serverName() { return serverName; }
    public static void setMapNameForTest(String map) { mapName = map; }
    public static void setServerNameForTest(String server) { serverName = server; }

    public static boolean isBedWarsHeightEnabled() {
        Module m = ModuleManager.getInstance().getModule("Bed Wars Height Limit");
        return m != null && m.isEnabled() && HypixelSafetyManager.isHypixel();
    }

    public static boolean isSessionStatsEnabled() {
        Module m = ModuleManager.getInstance().getModule("Session Stats");
        return m != null && m.isEnabled() && HypixelSafetyManager.isHypixel();
    }

    /**
     * Intercepts outgoing chat commands. Returns true if the command was a client command (like /session)
     * and should NOT be sent to the server.
     */
    public static boolean handleOutgoingCommand(String command) {
        if (command == null) return false;
        if (HyStats.handleCommand(command)) return true;
        if (SkyBlockSuite.handleCommand(command)) return true;
        String cmd = command.strip();
        if (cmd.startsWith("/")) cmd = cmd.substring(1).strip();
        if (cmd.equalsIgnoreCase("session") || cmd.toLowerCase(Locale.ROOT).startsWith("session ")) {
            String[] parts = cmd.split("\\s+");
            if (parts.length > 1 && parts[1].equalsIgnoreCase("reset")) {
                LevelHead.clearCache();
        HyStats.clearCache();
        OpenedChestHighlight.clear();
        LowBlockWarning.reset();
        BedWarsExtras.reset();
        SkyBlockSuite.reset();
        CrowdPlayerHider.clearNpcs();
        SessionTracker.reset();
                LadsGameBridge bridge = LadsGameBridge.get();
                if (bridge != null) bridge.displayChatMessage("§aSession stats have been reset.");
            } else {
                SessionTracker.printChatSummary();
            }
            return true;
        }
        return false;
    }

    /**
     * Replaces MVP++ shortcodes with their Unicode/text equivalents in outgoing chat for non-MVP++ players.
     */
    public static String handleOutgoingChat(String text) {
        if (text == null || text.isEmpty()) return text;
        if (on(CHAT_EMOTES) && !mvpPlusPlus) {
            return ChatEmotes.replaceEmotes(text);
        }
        return text;
    }

    /**
     * Masks streamer-sensitive chat lines (e.g. "Sending you to mini123A") when Streamer Mode is active.
     */
    public static String filterIncomingChat(String message) {
        if (message == null || !isStreamerMode()) return message;
        return HypixelChat.maskStreamerChat(message);
    }

    public static String getLocalPlayerName() {
        if (qaPlayerName != null && !qaPlayerName.isEmpty()) return qaPlayerName;
        return LadsGameBridge.get().getPlayerName();
    }

    public static void setPlayerNameForTest(String name) { qaPlayerName = name; }

    /** Pure decision logic for Auto Play Again. */
    public static boolean shouldReplay(String replayWhen, boolean waitParty, boolean selfOut, boolean gameOver, int partyAliveCount) {
        return shouldReplay(replayWhen, waitParty, selfOut, gameOver, partyAliveCount, true, false);
    }

    /** Pure decision logic for Auto Play Again with mode known and timeout flags. */
    public static boolean shouldReplay(String replayWhen, boolean waitParty, boolean selfOut, boolean gameOver, int partyAliveCount, boolean modeKnown, boolean timedOut) {
        if (gameOver) return true;
        if (!"After death".equalsIgnoreCase(replayWhen)) return false;
        if (!selfOut) return false;
        if (!waitParty) return true;
        if (timedOut) return true;
        if (!modeKnown && partyAliveCount > 0) return false;
        return partyAliveCount <= 0;
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
            PARTY_MEMBERS.clear();
            PARTY_ALIVE.clear();
            PARTY_ELIMINATED.clear();
            selfOut = replayed = partyListRequestedThisGame = inParty = false;
            selfDiedAt = partyListSent = 0;
            tipOff = false;
            world = null;
            mapName = serverName = null;
            OpenedChestHighlight.clear();
            LowBlockWarning.reset();
            BedWarsExtras.reset();
            SkyBlockSuite.reset();
            CrowdPlayerHider.clearNpcs();
            tipAt = now + 30 * SECOND; // the first tip half a minute after joining
        }
        if (current == null || !isActiveOnCurrentServer()) {
            QUEUE.clear();
            return;
        }
        if (currentWorld != world) {
            world = currentWorld;
            gameOver = started = selfOut = replayed = partyListRequestedThisGame = false;
            selfDiedAt = partyListSent = ggDue = 0;
            location = null;
            mapName = serverName = null;
            OpenedChestHighlight.clear();
            LowBlockWarning.reset();
            BedWarsExtras.reset();
            SkyBlockSuite.reset();
            CrowdPlayerHider.clearNpcs();
            QUEUE.removeIf(Queued::world);
            PARTY_ALIVE.clear();
            PARTY_ELIMINATED.clear();
            PARTY_ALIVE.addAll(PARTY_MEMBERS);
            String me = getLocalPlayerName();
            if (me != null && !me.isEmpty()) PARTY_ALIVE.remove(me.toLowerCase(Locale.ROOT));
            if (currentWorld != null && on(PLAY_AGAIN)) queue("/locraw", now + 2 * SECOND, true);
        }
        if (on(PLAY_AGAIN) && !replayed && selfOut && selfDiedAt != 0 && now - selfDiedAt >= 5 * 60 * SECOND) {
            triggerReplayIfEligible(now);
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
        if (lastCommand.equals("/tip all")) tipSent = now;
        if (lastCommand.equals("/locraw")) locrawSent = now;
        if (lastCommand.startsWith("/play ")) leaving(); // a gg still queued would land in the next game
        if (SENT.size() < 64) SENT.add(next.text());
        LOGGER.info("Lads Hypixel: sending {}", next.text());
        game.sendChatLine(next.text());
    }

    /** Leaving this game: its gg lines and /play no longer belong anywhere. */
    private static void leaving() { QUEUE.removeIf(q -> q.text().startsWith("/ac ") || q.text().startsWith("/play ")); }

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
        if (message == null || connection == null || !isActiveOnCurrentServer()) return false;
        HypixelChat.recordSender(message);
        // Answers to our own commands, by when each was sent (another command may have gone out since).
        if (tipSent != 0 && now - tipSent < 5 * SECOND && HypixelChat.unknownCommand(message)) {
            tipOff = true;
            LOGGER.info("Lads Hypixel: the server does not know /tip, Auto Tip is off until you reconnect");
        }
        HypixelChat.Locraw loc = HypixelChat.parseLocraw(message);
        if (loc != null) {
            location = new String[] { loc.gametype(), loc.mode() };
            mapName = loc.map();
            serverName = loc.server();
            if (locrawSent != 0 && now - locrawSent < 5 * SECOND) {
                locrawSent = 0;
                hidden++;
                triggerReplayIfEligible(now);
                return true; // our own question's answer
            }
        }

        String me = getLocalPlayerName();
        if (message.contains("[MVP++]")) {
            if (me != null && !me.isEmpty() && message.toLowerCase(Locale.ROOT).contains(me.toLowerCase(Locale.ROOT))) {
                mvpPlusPlus = true;
            }
        }
        SessionTracker.onServerChat(message, me);
        BedWarsExtras.onChat(message, now);
        SkyBlockSuite.onChat(message);
        if (BedWarsHeight.isLimitReachedMessage(message)) {
            int playerY = LadsGameBridge.get() != null ? LadsGameBridge.get().getPlayerY() : 0;
            BedWarsHeight.learnLimit(mapName, playerY);
        }

        // Party updates from chat or /party list
        if (HypixelChat.isPartyChat(message)) {
            inParty = true;
        }
        if (HypixelChat.partyDisbanded(message)) {
            inParty = false;
            PARTY_MEMBERS.clear();
            PARTY_ALIVE.clear();
            PARTY_ELIMINATED.clear();
            if (selfOut) triggerReplayIfEligible(now);
        }
        String pJoin = HypixelChat.partyJoin(message);
        if (pJoin != null) {
            inParty = true;
            if (pJoin.startsWith("YOU_JOINED:")) {
                PARTY_MEMBERS.clear();
                PARTY_ALIVE.clear();
                String leader = pJoin.substring("YOU_JOINED:".length()).toLowerCase(Locale.ROOT);
                PARTY_MEMBERS.add(leader);
                if (!PARTY_ELIMINATED.contains(leader)) PARTY_ALIVE.add(leader);
            } else {
                String member = pJoin.toLowerCase(Locale.ROOT);
                PARTY_MEMBERS.add(member);
                if ((me == null || !me.equalsIgnoreCase(member)) && !PARTY_ELIMINATED.contains(member)) {
                    PARTY_ALIVE.add(member);
                }
            }
        }
        String pLeave = HypixelChat.partyLeave(message);
        if (pLeave != null) {
            String member = pLeave.toLowerCase(Locale.ROOT);
            PARTY_MEMBERS.remove(member);
            if (PARTY_ALIVE.remove(member)) {
                if (selfOut) triggerReplayIfEligible(now);
            }
        }
        List<String> listMembers = HypixelChat.partyMembers(message);
        if (!listMembers.isEmpty()) {
            inParty = true;
            for (String m : listMembers) {
                String mLower = m.toLowerCase(Locale.ROOT);
                PARTY_MEMBERS.add(mLower);
                if ((me == null || !me.equalsIgnoreCase(m)) && !PARTY_ELIMINATED.contains(mLower)) {
                    PARTY_ALIVE.add(mLower);
                }
            }
        }
        if (HypixelChat.newGame(message)) leaving();
        if (partyListSent != 0 && now - partyListSent < 5 * SECOND && HypixelChat.isPartyListLine(message)) {
            hidden++;
            return true; // our own question's answer
        }

        if (!gameOver && HypixelChat.gameEnd(message)) {
            gameOver = true;
            started = false;
            gameEnded(now);
        }
        if (HypixelChat.gameStart(message)) {
            gameOver = false;
            started = true;
            selfOut = false;
            selfDiedAt = ggDue = 0;
            replayed = false;
            PARTY_ALIVE.clear();
            PARTY_ELIMINATED.clear();
            PARTY_ALIVE.addAll(PARTY_MEMBERS);
            if (me != null && !me.isEmpty()) PARTY_ALIVE.remove(me.toLowerCase(Locale.ROOT));
            if (on(AUTO_WHO)) queue("/who", now + 2 * SECOND, true);
            if (on(AUTO_GL)) {
                String gl = text(AUTO_GL, "Message");
                if (!gl.isEmpty()) queue("/ac " + gl, now + seconds(AUTO_GL, "Delay seconds", 0), true);
            }
            if (on(PLAY_AGAIN) && waitParty() && inParty && PARTY_MEMBERS.isEmpty() && !partyListRequestedThisGame) {
                partyListRequestedThisGame = true;
                partyListSent = now + 2 * SECOND;
                queue("/party list", partyListSent, true);
            }
        }
        String friend = HypixelChat.friendRequest(message);
        if (friend != null && on(AUTO_FRIEND)) {
            FRIENDS.values().removeIf(at -> now - at > 60 * SECOND);
            if (FRIENDS.putIfAbsent(friend.toLowerCase(java.util.Locale.ROOT), now) == null) queue("/friend accept " + friend, now, false);
        }

        // Elimination / death checks for Auto Play Again
        if (!gameOver && !replayed && on(PLAY_AGAIN)) {
            String gametype = location != null ? location[0] : null;
            if (!selfOut && HypixelChat.isSelfOut(gametype, message, me)) {
                selfOut = true;
                selfDiedAt = now;
                triggerReplayIfEligible(now);
            }
            String victim = HypixelChat.eliminatedPlayer(gametype, message);
            if (victim != null) {
                String vLower = victim.toLowerCase(Locale.ROOT);
                if (me != null && me.equalsIgnoreCase(victim)) {
                    if (!selfOut) {
                        selfOut = true;
                        selfDiedAt = now;
                        triggerReplayIfEligible(now);
                    }
                } else {
                    PARTY_ELIMINATED.add(vLower);
                    if (PARTY_ALIVE.remove(vLower)) {
                        if (selfOut) triggerReplayIfEligible(now);
                    }
                }
            }
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
            if (!message.isEmpty()) queue("/ac " + message, ggDue = first, true);
            if (!second.isEmpty()) queue("/ac " + second, ggDue = Math.max(first, ggDue) + seconds(AUTO_GG, "Second Message seconds", 2), true);
            // A /play already queued (we died first) waits for the gg lines too.
            for (int i = 0; i < QUEUE.size(); i++) {
                Queued q = QUEUE.get(i);
                if (ggDue != 0 && q.text().startsWith("/play ")) QUEUE.set(i, new Queued(Math.max(q.due(), ggDue + GAP), q.text(), q.world()));
            }
        }
        triggerReplayIfEligible(now);
    }

    private static void triggerReplayIfEligible(long now) {
        if (replayed || !on(PLAY_AGAIN)) return;
        String mode = replayWhenMode();
        boolean wait = waitParty();
        String gametype = location != null ? location[0] : null;
        boolean modeKnown = HypixelChat.isModeKnown(gametype);
        boolean timedOut = selfOut && selfDiedAt != 0 && (now - selfDiedAt >= 5 * 60 * SECOND);
        if (shouldReplay(mode, wait, selfOut, gameOver, PARTY_ALIVE.size(), modeKnown, timedOut)) {
            String play = location == null ? null : HypixelChat.playCommand(location[0], location[1]);
            if (play != null) {
                // After the gg lines (sending /play drops any still queued), however short the Play Again delay.
                queue(play, Math.max(now + seconds(PLAY_AGAIN, "Delay seconds", 3), ggDue == 0 ? 0 : ggDue + GAP), true);
                replayed = true;
            }
        }
    }

    /** QA: what the last /locraw said ({gametype, mode}), or null. */
    public static String[] location() { return location; }
    /** QA: Auto Tip stopped for this session (the server said it has no /tip). */
    public static boolean tipStopped() { return tipOff; }
    /** QA: lines waiting in the queue. */
    public static int queued() { return QUEUE.size(); }
    /** QA: the next Auto Tip is due now. */
    public static void tipNow(long now) { tipAt = now; }
    /** QA: party members currently known. */
    public static Set<String> partyMembers() { return Collections.unmodifiableSet(PARTY_MEMBERS); }
    /** QA: party members currently alive in this game. */
    public static Set<String> partyAlive() { return Collections.unmodifiableSet(PARTY_ALIVE); }
    /** QA: party members eliminated in this game. */
    public static Set<String> partyEliminated() { return Collections.unmodifiableSet(PARTY_ELIMINATED); }
    /** QA: true if currently in a party. */
    public static boolean isInParty() { return inParty; }
    /** QA/Test: set whether in party. */
    public static void setInPartyForTest(boolean in) { inParty = in; }
    /** QA: true once local player died/eliminated. */
    public static boolean isSelfOut() { return selfOut; }
    /** QA: re-arms for another game without clearing location or party. */
    public static void rearmForQa() {
        SENT.clear();
        QUEUE.clear();
        gameOver = false;
        started = false;
        selfOut = false;
        selfDiedAt = 0;
        replayed = false;
        partyListRequestedThisGame = false;
        PARTY_ALIVE.clear();
        PARTY_ELIMINATED.clear();
        PARTY_ALIVE.addAll(PARTY_MEMBERS);
        String me = getLocalPlayerName();
        if (me != null && !me.isEmpty()) PARTY_ALIVE.remove(me.toLowerCase(Locale.ROOT));
    }
    static void resetForTest() {
        QUEUE.clear(); FRIENDS.clear(); SENT.clear(); PARTY_MEMBERS.clear(); PARTY_ALIVE.clear(); PARTY_ELIMINATED.clear();
        connection = world = null; gameOver = started = tipOff = selfOut = replayed = partyListRequestedThisGame = inParty = false;
        lastSent = tipSent = locrawSent = selfDiedAt = partyListSent = ggDue = 0; lastCommand = ""; location = null;
        mapName = serverName = null; mvpPlusPlus = false;
        qaPlayerName = null;
        LevelHead.clearCache();
        HyStats.clearCache();
        OpenedChestHighlight.clear();
        LowBlockWarning.reset();
        BedWarsExtras.reset();
        SkyBlockSuite.reset();
        CrowdPlayerHider.clearNpcs();
        SessionTracker.reset();
        BedWarsHeight.resetForTest();
    }
}
