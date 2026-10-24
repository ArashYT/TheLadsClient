package com.thelads.core.client;

import static org.junit.jupiter.api.Assertions.*;

import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.TextOption;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class InventoryTweaksTest {
    private static Module lock() { return ModuleManager.getInstance().getModule(InventoryTweaks.SLOT_LOCK); }

    @AfterEach void restore() {
        lock().getOption(InventoryTweaks.LOCKED).reset();
        lock().setEnabled(false);
        InventoryTweaks.setQuery("");
        InventoryTweaks.tick();
    }

    @Test void lockedSlotsBlockOnlyMovesOutOfThem() {
        lock().setEnabled(true);
        ((TextOption) lock().getOption(InventoryTweaks.LOCKED)).setValue("0, 40,x,99");
        InventoryTweaks.tick();
        assertTrue(InventoryTweaks.locked(0));
        assertTrue(InventoryTweaks.locked(40));
        assertFalse(InventoryTweaks.locked(1));
        assertTrue(InventoryTweaks.blocks(0, InventoryTweaks.PICKUP, 0));
        assertTrue(InventoryTweaks.blocks(0, InventoryTweaks.THROW, 1));
        assertTrue(InventoryTweaks.blocks(0, InventoryTweaks.QUICK_MOVE, 0));
        assertFalse(InventoryTweaks.blocks(0, InventoryTweaks.CLONE, 2), "middle-click copy in creative moves nothing");
        assertTrue(InventoryTweaks.blocks(-1, InventoryTweaks.SWAP, 0), "a number key swapping a chest slot with locked hotbar 1");
        assertTrue(InventoryTweaks.blocks(5, InventoryTweaks.SWAP, InventoryTweaks.OFFHAND));
        assertFalse(InventoryTweaks.blocks(5, InventoryTweaks.SWAP, 3));
        assertFalse(InventoryTweaks.blocks(-1, InventoryTweaks.PICKUP, 0), "a chest's own slot");
    }

    @Test void toggleSavesTheList() {
        lock().setEnabled(true);
        InventoryTweaks.tick();
        InventoryTweaks.toggleLock(8);
        InventoryTweaks.toggleLock(3);
        assertEquals("3,8", ((TextOption) lock().getOption(InventoryTweaks.LOCKED)).getValue());
        InventoryTweaks.toggleLock(8);
        assertEquals("3", ((TextOption) lock().getOption(InventoryTweaks.LOCKED)).getValue());
        lock().setEnabled(false);
        InventoryTweaks.tick();
        assertFalse(InventoryTweaks.locked(3), "Slot Lock off locks nothing");
        assertFalse(InventoryTweaks.blocks(3, InventoryTweaks.PICKUP, 0));
    }

    @Test void searchMatchesNameOrId() {
        InventoryTweaks.setQuery(" Sword ");
        assertTrue(InventoryTweaks.matches("Diamond Sword", "minecraft:diamond_sword"));
        assertTrue(InventoryTweaks.matches("Excalibur", "minecraft:iron_sword"));
        assertFalse(InventoryTweaks.matches("Bread", "minecraft:bread"));
        InventoryTweaks.setQuery("");
        assertTrue(InventoryTweaks.matches("Bread", null));
    }

    @Test void armorSlots() {
        assertEquals(5, InventoryTweaks.armorMenuSlot(0));
        assertEquals(8, InventoryTweaks.armorMenuSlot(3));
        assertEquals(-1, InventoryTweaks.armorMenuSlot(4));
    }
}
