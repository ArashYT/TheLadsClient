package com.thelads.core.client;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;

/**
 * Compact chat (stack consecutive duplicate messages): detects incoming duplicate messages
 * and formats them with an incrementing counter suffix, e.g. " (x2)", " (x3)".
 */
public final class CompactChat {
    private static String lastMessage = null;
    private static int repeatCount = 1;
    private static long lastTimestamp = 0L;
    private static final long TIME_WINDOW_MS = 60_000L; // 60-second window

    private CompactChat() {}

    public static boolean enabled() {
        Module chat = ModuleManager.getInstance().getModule("Chat");
        return chat != null && chat.isEnabled() && chat.getOption("Compact Chat") instanceof BoolOption opt && opt.get();
    }

    public static String stripCount(String text) {
        if (text == null) return null;
        int idx = text.lastIndexOf(" §7(x");
        if (idx >= 0 && text.endsWith(")")) {
            return text.substring(0, idx);
        }
        int idxAlt = text.lastIndexOf(" (x");
        if (idxAlt >= 0 && text.endsWith(")")) {
            return text.substring(0, idxAlt);
        }
        return text;
    }

    public static String formatStacked(String baseText, int count) {
        if (count <= 1) return baseText;
        return baseText + " §7(x" + count + ")";
    }

    public static synchronized boolean checkDuplicate(String incoming) {
        if (!enabled() || incoming == null || incoming.trim().isEmpty()) {
            lastMessage = incoming;
            repeatCount = 1;
            lastTimestamp = System.currentTimeMillis();
            return false;
        }

        long now = System.currentTimeMillis();
        String cleanIncoming = stripCount(incoming);

        if (cleanIncoming.equals(lastMessage) && (now - lastTimestamp <= TIME_WINDOW_MS)) {
            repeatCount++;
            lastTimestamp = now;
            return true;
        }

        lastMessage = cleanIncoming;
        repeatCount = 1;
        lastTimestamp = now;
        return false;
    }

    public static synchronized int getRepeatCount() {
        return repeatCount;
    }

    public static synchronized String getLastMessage() {
        return lastMessage;
    }

    public static synchronized void reset() {
        lastMessage = null;
        repeatCount = 1;
        lastTimestamp = 0L;
    }
}
