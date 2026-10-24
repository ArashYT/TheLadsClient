package com.thelads.core.client;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;

/**
 * Chat search: filtering chat history by a typed search query.
 */
public final class ChatSearch {
    private static boolean active = false;
    private static String query = "";

    private ChatSearch() {}

    public static boolean enabled() {
        Module chat = ModuleManager.getInstance().getModule("Chat");
        return chat != null && chat.isEnabled() && chat.getOption("Chat Search") instanceof BoolOption opt && opt.get();
    }

    public static synchronized boolean isActive() {
        return active;
    }

    public static synchronized void setActive(boolean a) {
        active = a;
        if (!active) query = "";
    }

    public static synchronized void toggle() {
        setActive(!active);
    }

    public static synchronized String getQuery() {
        return query;
    }

    public static synchronized void setQuery(String q) {
        query = (q == null) ? "" : q.trim();
    }

    public static synchronized boolean matches(String text) {
        if (!enabled() || !active || query.isEmpty()) return true;
        if (text == null) return false;
        return text.toLowerCase().contains(query.toLowerCase());
    }
}
