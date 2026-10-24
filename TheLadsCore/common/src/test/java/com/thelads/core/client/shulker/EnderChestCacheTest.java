package com.thelads.core.client.shulker;

import com.thelads.core.client.shulker.EnderChestCache.CachedItem;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnderChestCacheTest {
    @AfterEach
    void tearDown() {
        EnderChestCache.clear();
    }

    @Test
    void testCacheAndClearLifecycle() {
        assertFalse(EnderChestCache.hasData());
        EnderChestCache.setSlot(0, new CachedItem("minecraft:diamond", 64, 0, "Diamond"));
        assertTrue(EnderChestCache.hasData());
        assertEquals("Diamond", EnderChestCache.getSlot(0).displayName());
        assertNull(EnderChestCache.getSlot(1));

        // Simulating disconnect / server switch clearing cache
        EnderChestCache.clear();
        assertFalse(EnderChestCache.hasData());
        assertNull(EnderChestCache.getSlot(0));
    }

    @Test
    void testContainerDetection() {
        assertTrue(EnderChestCache.isEnderChestContainer("Ender Chest"));
        assertTrue(EnderChestCache.isEnderChestContainer("Player's EnderChest"));
        assertFalse(EnderChestCache.isEnderChestContainer("Large Chest"));
        assertFalse(EnderChestCache.isEnderChestContainer(null));

        assertTrue(EnderChestCache.isEnderChestItem("minecraft:ender_chest"));
        assertFalse(EnderChestCache.isEnderChestItem("minecraft:chest"));
    }

    @Test
    void testTooltipFormatting() {
        EnderChestCache.setSlot(0, new CachedItem("minecraft:diamond_sword", 1, 0, "Diamond Sword"));
        EnderChestCache.setSlot(1, new CachedItem("minecraft:golden_apple", 16, 0, "Golden Apple"));

        List<String> lines = EnderChestCache.buildTooltipLines();
        assertFalse(lines.isEmpty());
        assertEquals("§6Ender Chest Contents:§r", lines.get(0));
        assertTrue(lines.contains(" §7- §f1x Diamond Sword"));
        assertTrue(lines.contains(" §7- §f16x Golden Apple"));
    }
}
