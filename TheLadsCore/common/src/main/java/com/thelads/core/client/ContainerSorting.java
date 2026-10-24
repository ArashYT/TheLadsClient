package com.thelads.core.client;

import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.ContainerSortingModule;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Client-side container inventory sorting (parity-1023).
 * Gated by HypixelSafetyManager (anti-ban rule: automated inventory clicks are risky/banned on untrusted servers).
 */
public final class ContainerSorting {
    public static final String SORT_LABEL = "Sort";
    public static final int BUTTON_WIDTH = 28;
    public static final int BUTTON_HEIGHT = 12;

    private static ContainerSortingModule cachedModule;

    private ContainerSorting() {}

    public static ContainerSortingModule module() {
        ContainerSortingModule m = cachedModule;
        if (m == null) {
            if (ModuleManager.getInstance().getModule(ContainerSortingModule.NAME) instanceof ContainerSortingModule found) {
                cachedModule = m = found;
            }
        }
        return m;
    }

    /** True only when the module is enabled and permitted by HypixelSafetyManager. */
    public static boolean isAllowed() {
        ContainerSortingModule m = module();
        return m != null && m.isEnabled() && HypixelSafetyManager.isAllowed(ContainerSortingModule.NAME);
    }

    public static boolean shouldSortContainer() {
        ContainerSortingModule m = module();
        return isAllowed() && m != null && m.sortContainer.get();
    }

    public static boolean shouldSortInventory() {
        ContainerSortingModule m = module();
        return isAllowed() && m != null && m.sortInventory.get();
    }

    public static int getButtonX(int leftPos, int imageWidth, boolean searchActive) {
        if (searchActive) {
            return leftPos + imageWidth - InventoryTweaks.SEARCH_WIDTH - BUTTON_WIDTH - 4;
        } else {
            return leftPos + imageWidth - BUTTON_WIDTH;
        }
    }

    public static int getButtonY(int topPos) {
        return topPos - BUTTON_HEIGHT - 2;
    }

    public static boolean isOverButton(double mouseX, double mouseY, int btnX, int btnY) {
        return mouseX >= btnX && mouseX < btnX + BUTTON_WIDTH && mouseY >= btnY && mouseY < btnY + BUTTON_HEIGHT;
    }

    /** Slot representation for clean-room sorting planning and unit testing. */
    public static final class SlotItem {
        public final int slotNumber;
        public final String itemId;
        public final int count;
        public final int maxStack;
        public final int damage;
        public final boolean locked;

        public SlotItem(int slotNumber, String itemId, int count, int maxStack, int damage, boolean locked) {
            this.slotNumber = slotNumber;
            this.itemId = itemId != null ? itemId : "";
            this.count = count;
            this.maxStack = maxStack > 0 ? maxStack : 64;
            this.damage = damage;
            this.locked = locked;
        }

        public boolean isEmpty() {
            return count <= 0 || itemId.isEmpty() || "minecraft:air".equalsIgnoreCase(itemId);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SlotItem that)) return false;
            return count == that.count && damage == that.damage
                && locked == that.locked && Objects.equals(itemId, that.itemId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(itemId, count, damage, locked);
        }
    }

    public static final Comparator<SlotItem> ITEM_COMPARATOR = (a, b) -> {
        if (a.isEmpty() && b.isEmpty()) return 0;
        if (a.isEmpty()) return 1;
        if (b.isEmpty()) return -1;
        int idComp = a.itemId.compareToIgnoreCase(b.itemId);
        if (idComp != 0) return idComp;
        int countComp = Integer.compare(b.count, a.count); // higher count first
        if (countComp != 0) return countComp;
        return Integer.compare(a.damage, b.damage);
    };

    /**
     * Computes the minimal sequence of slot swaps (pairs of slot numbers)
     * needed to sort the given slots in place.
     * Locked slots are completely skipped and never moved.
     */
    public static List<int[]> planSwaps(List<SlotItem> input) {
        if (input == null || input.size() <= 1) return Collections.emptyList();

        List<SlotItem> working = new ArrayList<>(input);
        List<SlotItem> sortable = new ArrayList<>();
        for (SlotItem item : working) {
            if (!item.locked) {
                sortable.add(item);
            }
        }
        sortable.sort(ITEM_COMPARATOR);

        List<int[]> swaps = new ArrayList<>();
        int targetIdx = 0;
        for (int i = 0; i < working.size() && targetIdx < sortable.size(); i++) {
            if (working.get(i).locked) continue;

            SlotItem desired = sortable.get(targetIdx++);
            SlotItem current = working.get(i);

            if (current.equals(desired)) continue;

            // Find where desired item currently is
            int foundIdx = -1;
            for (int j = i + 1; j < working.size(); j++) {
                if (!working.get(j).locked && working.get(j).equals(desired)) {
                    foundIdx = j;
                    break;
                }
            }

            if (foundIdx > i) {
                swaps.add(new int[]{input.get(i).slotNumber, input.get(foundIdx).slotNumber});
                SlotItem tmp = working.get(i);
                working.set(i, working.get(foundIdx));
                working.set(foundIdx, tmp);
            }
        }

        return swaps;
    }
}
