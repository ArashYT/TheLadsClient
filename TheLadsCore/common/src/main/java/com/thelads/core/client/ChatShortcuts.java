package com.thelads.core.client;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import java.util.HashMap;
import java.util.Map;

/**
 * Chat shortcuts and aliases: expands short typed triggers into full chat commands.
 * Visual-only: expands only what the user actively types before sending.
 */
public final class ChatShortcuts {
    private static final Map<String, String> SHORTCUTS = new HashMap<>();

    static {
        resetDefaults();
    }

    public static synchronized void resetDefaults() {
        SHORTCUTS.clear();
        SHORTCUTS.put("/l", "/lobby");
        SHORTCUTS.put("/hub", "/lobby");
        SHORTCUTS.put("/gm0", "/gamemode survival");
        SHORTCUTS.put("/gm1", "/gamemode creative");
        SHORTCUTS.put("/gm2", "/gamemode adventure");
        SHORTCUTS.put("/gm3", "/gamemode spectator");
        SHORTCUTS.put("/fw", "/friend wrap");
    }

    private ChatShortcuts() {}

    public static boolean enabled() {
        Module chat = ModuleManager.getInstance().getModule("Chat");
        return chat != null && chat.isEnabled() && chat.getOption("Chat Shortcuts") instanceof BoolOption opt && opt.get();
    }

    public static synchronized void registerShortcut(String trigger, String replacement) {
        if (trigger != null && replacement != null) {
            SHORTCUTS.put(trigger.trim().toLowerCase(), replacement.trim());
        }
    }

    public static synchronized String expand(String typedText) {
        if (!enabled() || typedText == null || !typedText.startsWith("/")) return typedText;

        String[] parts = typedText.split(" ", 2);
        String trigger = parts[0].toLowerCase();
        String replacement = SHORTCUTS.get(trigger);
        if (replacement != null) {
            return parts.length > 1 ? replacement + " " + parts[1] : replacement;
        }
        return typedText;
    }
}
