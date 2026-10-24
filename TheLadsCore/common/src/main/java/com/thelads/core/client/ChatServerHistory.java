package com.thelads.core.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * History per Server (Chat module): each multiplayer server's chat is kept when the player leaves it (vanilla clears the chat on
 * disconnect, 1.8.9 on the main menu, and 1.8.9 keeps one server's chat shown on the next) and comes back when they join it again
 * in the same game session. The adapters hand in their own message lists (newest first); messages are kept as the game's own
 * objects, so they keep their styles, links and the time they arrived. Client thread only.
 */
public final class ChatServerHistory {
    /** Newest messages kept per server, and servers kept. */
    static final int MESSAGES = 1000, SERVERS = 16;
    private static final Map<String, List<Object>> SAVED = new LinkedHashMap<>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, List<Object>> eldest) { return size() > SERVERS; }
    };
    /** The server whose messages the chat shows now; null in singleplayer, menus or after the chat was cleared. */
    private static String shown;

    private ChatServerHistory() {}

    /** The chat is being cleared: its messages are kept for the server they came from. */
    public static void clearing(List<?> messages) {
        save(messages);
        shown = null;
    }

    /**
     * Before a message is added and once a tick: {@code server} is the multiplayer address now (null in singleplayer and menus).
     * On a new server the chat's messages are swapped: the last server's are kept, this one's come back (older than any message
     * already there). True when the list changed and the chat must be laid out again.
     */
    @SuppressWarnings("unchecked")
    public static <M> boolean joined(String server, List<M> messages) {
        if (server == null || server.equals(shown)) return false;
        boolean leftAnother = shown != null;
        save(messages);
        shown = server;
        List<M> saved = (List<M>) (List<?>) SAVED.remove(server);
        if (!ChatTweaks.serverHistory) return false;
        if (leftAnother) messages.clear();
        if (saved != null) messages.addAll(saved);
        return leftAnother || saved != null;
    }

    private static void save(List<?> messages) {
        if (shown == null || !ChatTweaks.serverHistory || messages.isEmpty()) return;
        SAVED.put(shown, new ArrayList<>(messages.subList(0, Math.min(MESSAGES, messages.size()))));
    }

    static void reset() {
        SAVED.clear();
        shown = null;
    }
}
