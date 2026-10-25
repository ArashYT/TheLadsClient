package com.thelads.core.client;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContainerSortingTest {
    @Test
    void comparatorSorting() {
        ContainerSorting.SlotItem cobble = new ContainerSorting.SlotItem(0, "minecraft:cobblestone", 64, 64, 0, false);
        ContainerSorting.SlotItem dirt = new ContainerSorting.SlotItem(1, "minecraft:dirt", 32, 64, 0, false);
        ContainerSorting.SlotItem diamond = new ContainerSorting.SlotItem(2, "minecraft:diamond", 10, 64, 0, false);
        ContainerSorting.SlotItem empty = new ContainerSorting.SlotItem(3, "minecraft:air", 0, 64, 0, false);

        assertTrue(ContainerSorting.ITEM_COMPARATOR.compare(cobble, dirt) < 0, "cobblestone before dirt alphabetically");
        assertTrue(ContainerSorting.ITEM_COMPARATOR.compare(diamond, dirt) < 0, "diamond before dirt alphabetically");
        assertTrue(ContainerSorting.ITEM_COMPARATOR.compare(dirt, empty) < 0, "non-empty items before empty slots");
    }

    @Test
    void planSwapsSimple() {
        List<ContainerSorting.SlotItem> slots = new ArrayList<>();
        slots.add(new ContainerSorting.SlotItem(0, "minecraft:dirt", 64, 64, 0, false));
        slots.add(new ContainerSorting.SlotItem(1, "minecraft:cobblestone", 64, 64, 0, false));

        List<int[]> swaps = ContainerSorting.planSwaps(slots);
        assertEquals(1, swaps.size());
        assertEquals(0, swaps.get(0)[0]);
        assertEquals(1, swaps.get(0)[1]);
    }

    @Test
    void planSwapsThreeItemsPermutation() {
        List<ContainerSorting.SlotItem> slots = new ArrayList<>();
        slots.add(new ContainerSorting.SlotItem(0, "minecraft:dirt", 64, 64, 0, false));
        slots.add(new ContainerSorting.SlotItem(1, "minecraft:stone", 64, 64, 0, false));
        slots.add(new ContainerSorting.SlotItem(2, "minecraft:cobblestone", 64, 64, 0, false));

        List<int[]> swaps = ContainerSorting.planSwaps(slots);
        // Desired order alphabetically: cobblestone (slot 0), dirt (slot 1), stone (slot 2)
        String[] state = new String[]{"minecraft:dirt", "minecraft:stone", "minecraft:cobblestone"};
        for (int[] swap : swaps) {
            String temp = state[swap[0]];
            state[swap[0]] = state[swap[1]];
            state[swap[1]] = temp;
        }
        assertArrayEquals(new String[]{"minecraft:cobblestone", "minecraft:dirt", "minecraft:stone"}, state);
    }

    @Test
    void planSwapsIdenticalStacksNoUnnecessarySwaps() {
        List<ContainerSorting.SlotItem> slots = new ArrayList<>();
        slots.add(new ContainerSorting.SlotItem(0, "minecraft:cobblestone", 64, 64, 0, false));
        slots.add(new ContainerSorting.SlotItem(1, "minecraft:cobblestone", 64, 64, 0, false));
        slots.add(new ContainerSorting.SlotItem(2, "minecraft:stone", 64, 64, 0, false));

        List<int[]> swaps = ContainerSorting.planSwaps(slots);
        // Already in order: cobble, cobble, stone. Identical cobble stacks should not swap.
        assertTrue(swaps.isEmpty(), "Already sorted identical stacks should require 0 swaps");
    }

    @Test
    void planSwapsPreservesLockedSlots() {
        List<ContainerSorting.SlotItem> slots = new ArrayList<>();
        // Slot 0 is locked with dirt
        slots.add(new ContainerSorting.SlotItem(0, "minecraft:dirt", 64, 64, 0, true));
        // Slot 1 has stone
        slots.add(new ContainerSorting.SlotItem(1, "minecraft:stone", 64, 64, 0, false));
        // Slot 2 has cobblestone
        slots.add(new ContainerSorting.SlotItem(2, "minecraft:cobblestone", 64, 64, 0, false));

        List<int[]> swaps = ContainerSorting.planSwaps(slots);
        for (int[] swap : swaps) {
            assertNotEquals(0, swap[0], "locked slot 0 must never be swapped");
            assertNotEquals(0, swap[1], "locked slot 0 must never be swapped");
        }
    }

    @Test
    void emptyListAndSingleItem() {
        assertTrue(ContainerSorting.planSwaps(null).isEmpty());
        assertTrue(ContainerSorting.planSwaps(new ArrayList<>()).isEmpty());
        List<ContainerSorting.SlotItem> single = List.of(new ContainerSorting.SlotItem(0, "minecraft:stone", 1, 64, 0, false));
        assertTrue(ContainerSorting.planSwaps(single).isEmpty());
    }
}
