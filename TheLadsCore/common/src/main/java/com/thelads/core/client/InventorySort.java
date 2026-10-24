package com.thelads.core.client;

import com.thelads.core.modules.InventorySortModule;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Inventory sort and middle-click armor swap helper.
 * GATED: automated multiple clicks / inventory sorting can trigger packet-rate anticheats.
 */
public final class InventorySort {
    public interface SortableSlot<I> {
        int index();
        I item();
        String itemId();
        int count();
    }

    private InventorySort() {}

    public static boolean isAllowed() {
        return InventorySortModule.isAllowed();
    }

    public static boolean isArmorSwapEnabled() {
        return isAllowed() && InventorySortModule.armorSwapEnabled();
    }

    public static boolean isArmorItem(String itemId) {
        if (itemId == null) return false;
        String s = itemId.toLowerCase();
        return s.contains("helmet") || s.contains("chestplate") || s.contains("leggings") || s.contains("boots")
                || s.contains("elytra") || s.contains("cap") || s.contains("tunic") || s.contains("pants");
    }

    public static <I, S extends SortableSlot<I>> List<S> sortSlots(List<S> slots) {
        if (!isAllowed() || slots == null) return slots;
        List<S> sorted = new ArrayList<>(slots);
        sorted.sort((a, b) -> {
            String idA = a.itemId();
            String idB = b.itemId();
            if (idA == null && idB == null) return Integer.compare(b.count(), a.count());
            if (idA == null) return 1;
            if (idB == null) return -1;
            int cmp = idA.compareTo(idB);
            return cmp != 0 ? cmp : Integer.compare(b.count(), a.count());
        });
        return sorted;
    }
}
