package com.thelads.core.client;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Chat history kept per server across disconnects.
 */
public final class ChatPersistence {
    private static final int MAX_PER_SERVER = 1000;
    private static final Map<String, List<String>> PER_SERVER_HISTORY = new HashMap<>();

    private ChatPersistence() {}

    public static boolean enabled() {
        Module chat = ModuleManager.getInstance().getModule("Chat");
        return chat != null && chat.isEnabled() && chat.getOption("Persistent History") instanceof BoolOption opt && opt.get();
    }

    public static synchronized void recordMessage(String server, String line) {
        if (!enabled() || server == null || line == null) return;
        String key = server.toLowerCase();
        List<String> list = PER_SERVER_HISTORY.computeIfAbsent(key, k -> new ArrayList<>());
        list.add(line);
        if (list.size() > MAX_PER_SERVER) {
            list.remove(0);
        }
    }

    public static synchronized List<String> getHistory(String server) {
        if (!enabled() || server == null) return Collections.emptyList();
        List<String> list = PER_SERVER_HISTORY.get(server.toLowerCase());
        if (list == null) return Collections.emptyList();
        return new ArrayList<>(list);
    }

    public static synchronized void clear() {
        PER_SERVER_HISTORY.clear();
    }
}
