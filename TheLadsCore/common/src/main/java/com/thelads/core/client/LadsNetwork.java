package com.thelads.core.client;

import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.ladsapi.LadsApiClient;
import java.util.Locale;
import java.util.function.Predicate;

/** The Lads Network: our own server, joined from the title screen and named in Discord when its LadsClientAPI says hello. */
public final class LadsNetwork {
    public static final String NAME = "The Lads Network";
    public static final String ADDRESS = "mc.arashyt.ca";
    /** The title screen's grid cells fit about 9 characters ("The Lads Network" showed as "The Lad..."). */
    public static final String TITLE_LABEL = "Network";
    public static final String TOOLTIP = "Join " + NAME + " (" + ADDRESS + ")";
    public static final String HUB_LABEL = "Hub";
    /** Hub commands to look for in the server's command tree when its hello names none (a plugin not updated yet). */
    private static final String[] HUB_COMMANDS = {"hub", "lobby"};

    private LadsNetwork() {
    }

    /** The title screen offers the network in the client profiles, not in a modpack (the launcher passes -Dlads.modpack=true). */
    public static boolean titleButton() {
        return !Boolean.getBoolean("lads.modpack");
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
        return isAddress(LadsGameBridge.get().getServerAddress());
    }

    /** The game or lobby the network's hello named, or null: off the network, or a plugin that does not send it. */
    public static String game() {
        String game = LadsApiClient.networkGame();
        return connected() && !game.isEmpty() ? game : null;
    }

    /**
     * The command that goes to the hub ("/hub"), or null when off the network or the server has none: the one its hello names, else
     * "hub" or "lobby" if {@code serverHas} finds it in the command tree (1.8.9 has no tree: only the hello).
     */
    public static String hubCommand(Predicate<String> serverHas) {
        if (!connected()) return null;
        String named = LadsApiClient.networkHub();
        if (!named.isEmpty()) return named;
        for (String command : HUB_COMMANDS) if (serverHas.test(command)) return "/" + command;
        return null;
    }

    /** The pause menu's button: Network away from the network, Hub on it, none on it without a hub or in a modpack. */
    public static String pauseLabel(Predicate<String> serverHas) {
        if (!titleButton()) return null;
        return !connected() ? TITLE_LABEL : hubCommand(serverHas) != null ? HUB_LABEL : null;
    }
}
