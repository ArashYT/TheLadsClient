package com.thelads.core.client.shulker;

import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.EnderChestPeekModule;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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

    public static synchronized List<CachedItem> getAllSlots() {
        return Collections.unmodifiableList(Arrays.asList(SLOTS));
    }

    public static synchronized boolean hasData() {
        return hasCachedData;
    }

    public static synchronized void clear() {
        Arrays.fill(SLOTS, null);
        hasCachedData = false;
    }

    public static boolean isEnabled() {
        com.thelads.core.config.Module m = ModuleManager.getInstance().getModule(EnderChestPeekModule.NAME);
        return m != null && m.isEnabled();
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

    /** Formats tooltip lines for the cached ender chest contents. */
    public static List<String> buildTooltipLines() {
        if (!hasData()) return Collections.emptyList();
        List<String> lines = new ArrayList<>();
        lines.add("§6Ender Chest Contents:§r");
        int count = 0;
        for (CachedItem item : SLOTS) {
            if (item != null && item.count() > 0) {
                lines.add(" §7- §f" + item.count() + "x " + item.displayName());
                count++;
            }
        }
        if (count == 0) {
            lines.add(" §8(Empty)§r");
        }
        return lines;
    }
}
