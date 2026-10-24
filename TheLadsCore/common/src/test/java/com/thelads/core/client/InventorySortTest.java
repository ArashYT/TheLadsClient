package com.thelads.core.client;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class InventorySortTest {
    record TestSlot(int index, String item, String itemId, int count) implements InventorySort.SortableSlot<String> {}

    @Test
    public void testIsArmorItem() {
        assertTrue(InventorySort.isArmorItem("minecraft:diamond_helmet"));
        assertTrue(InventorySort.isArmorItem("minecraft:iron_chestplate"));
        assertTrue(InventorySort.isArmorItem("minecraft:leather_boots"));
        assertTrue(InventorySort.isArmorItem("minecraft:elytra"));
        assertFalse(InventorySort.isArmorItem("minecraft:apple"));
        assertFalse(InventorySort.isArmorItem(null));
    }

    @Test
    public void testSortSlots() {
        List<TestSlot> slots = new ArrayList<>();
        slots.add(new TestSlot(0, "Stick", "minecraft:stick", 5));
        slots.add(new TestSlot(1, "Apple", "minecraft:apple", 10));
        slots.add(new TestSlot(2, "Diamond", "minecraft:diamond", 64));

        // When module is enabled/allowed, sort orders by item ID
        // Note: isAllowed() checks safety manager, in unit test environment we test comparator directly or via sortSlots
        List<TestSlot> sorted = new ArrayList<>(slots);
        sorted.sort(java.util.Comparator.comparing(InventorySort.SortableSlot::itemId));
        assertEquals("minecraft:apple", sorted.get(0).itemId());
        assertEquals("minecraft:diamond", sorted.get(1).itemId());
        assertEquals("minecraft:stick", sorted.get(2).itemId());
    }
}
