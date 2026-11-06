package com.thelads.core.client;

import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.ladsapi.LadsApiClient;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/** The Lads Network: our own server, joined from the title screen/hub dialog and named in Discord when its LadsClientAPI says hello. */
public final class LadsNetwork {
    public static final String NAME = "The Lads Network";
    public static final String ADDRESS = "mc.arashyt.ca";
    /** Full title and pause menu button label; spans its own row in both title layouts so it never truncates. */
    public static final String TITLE_LABEL = "The Lads Network";
    public static final String TOOLTIP = "Join " + NAME + " (" + ADDRESS + ")";
    public static final String HUB_LABEL = "Hub";
    /** Hub commands to look for in the server's command tree when its hello names none (a plugin not updated yet). */
    private static final String[] HUB_COMMANDS = {"hub", "lobby"};
    private static final int SETTLE_TICKS = 30;

    public record SubServer(String id, String name, String subtitle, String command) {}

    public static final List<SubServer> SUB_SERVERS = List.of(
        new SubServer("lobby", "Main Lobby", "Central hub, cosmetics & portals", "/server lobby"),
        new SubServer("parkour", "Parkour", "Timed courses, checkpoints & leaderboards", "/server parkour"),
        new SubServer("pvparena", "PvP Arena", "FFA kit combat & killstreaks", "/server pvparena"),
        new SubServer("pvpduels", "PvP Duels", "1v1 ranked & unranked queues", "/server pvpduels"),
        new SubServer("smp", "Lads SMP", "Survival multiplayer world", "/server smp")
    );

    private static volatile Predicate<String> commandLookup = command -> false;
    private static volatile BiConsumer<String, String> connector;
    private static volatile String pendingCommand;
    private static volatile int settleTicks;

    private LadsNetwork() {
    }

    /** Installed by the version bridge at startup so common UI can inspect the server's command tree without version coupling. */
    public static void installCommandLookup(Predicate<String> lookup) {
        commandLookup = lookup != null ? lookup : command -> false;
    }

    /** Installed by the version bridge at startup so common UI can initiate a server connection with packs enabled. */
    public static void installConnector(BiConsumer<String, String> serverConnector) {
        connector = serverConnector;
    }

    public static SubServer findSubServer(String id) {
        if (id == null) return null;
        for (SubServer sub : SUB_SERVERS) {
            if (sub.id().equalsIgnoreCase(id)) return sub;
        }
        return null;
    }

    /** The title screen offers the network in the client profiles, not in a modpack (the launcher passes -Dlads.modpack=true). */
    public static boolean titleButton() {
        return !Boolean.getBoolean("lads.modpack");
    }

    /**
     * The entry kept first in the server list, as the launcher pins it (ServerListService.PinLadsNetworkAsync, ServerListMerge.pinFirst here):
     * in the client profiles, like the title button, not in a modpack's own list.
     */
    public static boolean isPinned(String address) {
        return titleButton() && com.thelads.core.shared.ServerListMerge.key(address).equals(ADDRESS);
    }

    /** {@code host} (no port) is arashyt.ca or one of its subdomains: every server of the network goes through it. */
    public static boolean isHost(String host) {
        if (host == null) return false;
        String h = host.trim().toLowerCase(Locale.ROOT).replaceAll("\\.+$", ""); // "mc.arashyt.ca." is the same host
        return h.equals("arashyt.ca") || h.endsWith(".arashyt.ca");
    }

    /** {@link #isHost} for a server address as the game and friends' presence give it: host or host:port. */
    public static boolean isAddress(String address) {
        if (address == null) return false;
        String a = address.trim();
        int port = a.lastIndexOf(':');
        return isHost(port > 0 ? a.substring(0, port) : a);
    }

    /** Playing on the network right now. Everything below, and anything that sends a command, goes through this (Anti-Ban Rule). */
    public static boolean connected() {
        return isConnected(LadsGameBridge.get());
    }

    /** Playing on the network on the given bridge. */
    public static boolean isConnected(LadsGameBridge bridge) {
        return bridge != null && isAddress(bridge.getServerAddress());
    }

    /** The game or lobby the network's hello named, or null: off the network, or a plugin that does not send it. */
    public static String game() {
        String game = LadsApiClient.networkGame();
        return connected() && !game.isEmpty() ? game : null;
    }

    /** Dynamic hub command using the installed version command-tree lookup. */
    public static String hubCommand() {
        return hubCommand(commandLookup);
    }

    /**
     * The command that goes to the hub ("/hub"), or null when off the network or the server has none: the one its hello names, else
     * "hub" or "lobby" if {@code serverHas} finds it in the command tree (1.8.9 has no tree: only the hello).
     */
    public static String hubCommand(Predicate<String> serverHas) {
        if (!connected()) return null;
        String named = LadsApiClient.networkHub();
        if (!named.isEmpty()) return named;
        Predicate<String> lookup = serverHas != null ? serverHas : command -> false;
        for (String command : HUB_COMMANDS) if (lookup.test(command)) return "/" + command;
        return null;
    }

    /** The pause menu's button: Network away from the network, Hub on it, none on it without a hub or in a modpack. */
    public static String pauseLabel(Predicate<String> serverHas) {
        if (!titleButton()) return null;
        return !connected() ? TITLE_LABEL : hubCommand(serverHas) != null ? HUB_LABEL : null;
    }

    public static String pendingCommand() {
        return pendingCommand;
    }

    public static void clearPendingCommand() {
        pendingCommand = null;
        settleTicks = 0;
    }

    /** Connects to The Lads Network when away, or returns to the network hub when already connected. */
    public static void join(LadsGameBridge bridge) {
        LadsGameBridge target = bridge != null ? bridge : LadsGameBridge.get();
        clearPendingCommand();
        if (isConnected(target) && target.hasPlayer()) {
            String hub = target == LadsGameBridge.get() ? hubCommand() : null;
            target.sendChatLine(hub != null ? hub : "/server lobby");
            return;
        }
        connect(target, NAME, ADDRESS);
    }

    /** Switches to a sub-server immediately when on The Lads Network, or connects first and queues the warp safely. */
    public static void joinSubServer(LadsGameBridge bridge, SubServer sub) {
        if (sub == null) {
            join(bridge);
            return;
        }
        LadsGameBridge target = bridge != null ? bridge : LadsGameBridge.get();
        if (isConnected(target) && target.hasPlayer()) {
            clearPendingCommand();
            target.sendChatLine(sub.command());
            return;
        }
        pendingCommand = sub.command();
        settleTicks = 0;
        connect(target, NAME, ADDRESS);
    }

    /**
     * Called once per client tick: dispatches a queued sub-server warp after login onto {@code arashyt.ca}, and immediately cancels
     * any queued command if the player enters Singleplayer or any non-Lads server (strict Anti-Ban Rule).
     */
    public static void tick(LadsGameBridge bridge) {
        String cmd = pendingCommand;
        if (cmd == null || bridge == null) return;
        if (!bridge.isIngame() || !bridge.hasPlayer()) {
            settleTicks = 0;
            return;
        }
        if (!isConnected(bridge)) {
            clearPendingCommand();
            return;
        }
        int ticks = ++settleTicks;
        if (ticks >= SETTLE_TICKS) {
            clearPendingCommand();
            if (isConnected(bridge) && bridge.hasPlayer()) {
                bridge.sendChatLine(cmd);
            }
        }
    }

    private static void connect(LadsGameBridge bridge, String name, String address) {
        if (bridge != null) {
            try {
                Method m = bridge.getClass().getMethod("connectToServer", String.class, String.class);
                m.setAccessible(true);
                m.invoke(bridge, name, address);
                return;
            } catch (NoSuchMethodException ignored) {
                // Fall back to installed static connector when bridge does not declare connectToServer.
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Failed to invoke connectToServer on " + bridge.getClass().getName(), e);
            }
        }
        BiConsumer<String, String> fn = connector;
        if (fn != null) {
            fn.accept(name, address);
        }
    }
}
