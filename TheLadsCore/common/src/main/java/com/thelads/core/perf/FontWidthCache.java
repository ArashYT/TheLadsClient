package com.thelads.core.perf;

import java.util.concurrent.ConcurrentHashMap;

/**
 * High-performance bounded thread-safe text width cache.
 * Avoids lock contention on the hit path by using a lock-free ConcurrentHashMap.
 * Bounded to 4,096 entries; clears when capacity is reached to prevent unbounded memory growth.
 * Invalidation occurs on resource reload or font change via {@link #clear()}.
 */
public final class FontWidthCache {
    public static final int MAX_ENTRIES = 4096;
    public static volatile boolean enabled = true;

    private static final ConcurrentHashMap<String, Integer> CACHE = new ConcurrentHashMap<>(1024);

    private FontWidthCache() {}

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    /**
     * Retrieves the cached width for the given string without locking.
     * Returns null on cache miss or when disabled.
     */
    public static Integer get(String text) {
        if (!enabled || text == null) return null;
        return CACHE.get(text);
    }

    /**
     * Stores the computed width for the given string.
     * When capacity reaches {@link #MAX_ENTRIES}, flushes the cache to bound memory.
     */
    public static void put(String text, int width) {
        if (!enabled || text == null) return;
        if (CACHE.size() >= MAX_ENTRIES) {
            CACHE.clear();
        }
        CACHE.put(text, width);
    }

    /**
     * Invalidation: flushes all cached measurements (called on font reload or resource pack reload).
     */
    public static void clear() {
        CACHE.clear();
    }

    public static int size() {
        return CACHE.size();
    }
}
