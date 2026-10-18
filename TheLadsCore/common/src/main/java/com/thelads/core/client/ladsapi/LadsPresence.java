package com.thelads.core.client.ladsapi;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who on this server runs the Lads Client, for the badge in tab and nametags. Two sources: the server's presence messages
 * (LadsClientAPI servers) and the cosmetics relay room (any server; talks only to bot.arashyt.ca, never to the Minecraft server).
 * Asked per player per frame: {@link #isLads} doesn't allocate.
 */
public final class LadsPresence {
    private static final Set<UUID> SERVER = ConcurrentHashMap.newKeySet(), RELAY = ConcurrentHashMap.newKeySet();

    private LadsPresence() {}

    public static boolean isLads(UUID player) {
        return player != null && (SERVER.contains(player) || RELAY.contains(player));
    }

    /** presence {full:true}: replaces the server's set. */
    public static void serverReplace(Collection<UUID> players) {
        SERVER.clear();
        SERVER.addAll(players);
    }

    public static void serverAdd(Collection<UUID> players) {
        SERVER.addAll(players);
    }

    public static void serverRemove(Collection<UUID> players) {
        SERVER.removeAll(players);
    }

    /** Server reset / disconnect. */
    public static void serverClear() {
        SERVER.clear();
    }

    /** Relay snapshot/join. */
    public static void relayAdd(UUID player) {
        if (player != null) RELAY.add(player);
    }

    /** Relay leave. */
    public static void relayRemove(UUID player) {
        if (player != null) RELAY.remove(player);
    }

    /** Relay room change or disconnect. */
    public static void relayClear() {
        RELAY.clear();
    }
}
