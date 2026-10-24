package com.thelads.core.client.shulker;

import java.util.Arrays;

/**
 * Caches personal Ender Chest inventory (27 slots) when opened, allowing instant peek preview upon hovering (parity with Badlion/OneClient).
 */
public final class EnderChestCache {
    public record CachedItem(String id, int count, int damage, String displayName) {}

    private static final CachedItem[] SLOTS = new CachedItem[27];
    private static boolean hasCachedData = false;

    private EnderChestCache() {}

    public static synchronized void setSlot(int index, CachedItem item) {
        if (index >= 0 && index < 27) {
            SLOTS[index] = item;
            hasCachedData = true;
        }
    }

    public static synchronized CachedItem getSlot(int index) {
        if (index >= 0 && index < 27) {
            return SLOTS[index];
        }
        return null;
    }

    public static synchronized boolean hasData() {
        return hasCachedData;
    }

    public static synchronized void clear() {
        Arrays.fill(SLOTS, null);
        hasCachedData = false;
    }

    public static boolean isEnderChestContainer(String title) {
        if (title == null) return false;
        String t = title.toLowerCase();
        return t.contains("ender chest") || t.contains("enderchest");
    }

    public static boolean isEnderChestItem(String id) {
        if (id == null) return false;
        String s = id.toLowerCase();
        return s.contains("ender_chest") || s.contains("enderchest");
    }
}
