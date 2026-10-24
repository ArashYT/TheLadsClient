package com.thelads.core.client.shulker;

import com.thelads.core.modules.EnderChestPeekModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnderChestCacheTest {
    @BeforeEach
    void setUp() {
        EnderChestCache.clear();
    }

    @Test
    void testDefaults() {
        EnderChestPeekModule mod = new EnderChestPeekModule();
        assertFalse(mod.isEnabled(), "Gated module must default off");
        assertTrue(mod.showEmptySlots.get());
    }

    @Test
    void testCacheOperations() {
        assertFalse(EnderChestCache.hasData());
        assertNull(EnderChestCache.getSlot(0));

        EnderChestCache.setSlot(0, new EnderChestCache.CachedItem("minecraft:diamond", 64, 0, "Diamonds"));
        assertTrue(EnderChestCache.hasData());
        var item = EnderChestCache.getSlot(0);
        assertNotNull(item);
        assertEquals("minecraft:diamond", item.id());
        assertEquals(64, item.count());

        EnderChestCache.clear();
        assertFalse(EnderChestCache.hasData());
        assertNull(EnderChestCache.getSlot(0));
    }

    @Test
    void testTitleAndItemMatching() {
        assertTrue(EnderChestCache.isEnderChestContainer("Ender Chest"));
        assertTrue(EnderChestCache.isEnderChestContainer("Player's Ender Chest"));
        assertFalse(EnderChestCache.isEnderChestContainer("Chest"));

        assertTrue(EnderChestCache.isEnderChestItem("minecraft:ender_chest"));
        assertFalse(EnderChestCache.isEnderChestItem("minecraft:chest"));
    }
}
