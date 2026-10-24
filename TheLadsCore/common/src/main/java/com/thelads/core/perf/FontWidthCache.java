package com.thelads.core.perf;

import com.thelads.core.modules.PerformanceManagerModule;
import java.util.HashMap;
import java.util.Map;

/**
 * Global text-width cache for modern Minecraft (1.20.1 and 26.3; parity with 1.8.9 FontCache189).
 * Eliminates repetitive string width calculations across HUD, tab list, chat, scoreboard, and containers.
 * Bounded size (5000 entries) with fast lookup; cleared on font/resource-pack reloads.
 */
public final class FontWidthCache {
    public static final int MAX_ENTRIES = 5000;
    private static final Map<String, Integer> CACHE = new HashMap<>();
    private static final Object LOCK = new Object();
    private static volatile boolean disabled = "false".equalsIgnoreCase(System.getProperty("thelads.fontWidthCache"));

    private FontWidthCache() {}

    public static boolean isEnabled() {
        return !disabled && PerformanceManagerModule.bool("Global Text Width Caching", true);
    }

    public static void setDisabled(boolean off) {
        disabled = off;
    }

    public static int get(String text) {
        if (text == null || !isEnabled()) return -1;
        synchronized (LOCK) {
            Integer width = CACHE.get(text);
            return width != null ? width : -1;
        }
    }

    public static void put(String text, int width) {
        if (text == null || width < 0 || !isEnabled()) return;
        synchronized (LOCK) {
            if (CACHE.size() >= MAX_ENTRIES) CACHE.clear();
            CACHE.put(text, width);
        }
    }

    public static void clear() {
        synchronized (LOCK) {
            CACHE.clear();
        }
    }

    public static int size() {
        synchronized (LOCK) {
            return CACHE.size();
        }
    }
}
