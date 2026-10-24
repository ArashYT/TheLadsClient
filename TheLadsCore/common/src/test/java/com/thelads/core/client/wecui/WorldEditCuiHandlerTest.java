package com.thelads.core.client.wecui;

import com.thelads.core.modules.WorldEditCuiModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldEditCuiHandlerTest {
    @BeforeEach
    void setUp() {
        WorldEditCuiHandler.clear();
    }

    @Test
    void testDefaults() {
        WorldEditCuiModule mod = new WorldEditCuiModule();
        assertFalse(mod.isEnabled(), "Gated module must default off");
        assertTrue(mod.showGrid.get());
    }

    @Test
    void testSelectionMessages() {
        assertFalse(WorldEditCuiHandler.hasSelection());

        // Set p0 and p1
        WorldEditCuiHandler.handleMessage("s|cuboid");
        WorldEditCuiHandler.handleMessage("p|0|10|60|20|1");
        assertFalse(WorldEditCuiHandler.hasSelection());

        WorldEditCuiHandler.handleMessage("p|1|15|65|25|216");
        assertTrue(WorldEditCuiHandler.hasSelection());

        var box = WorldEditCuiHandler.getSelection();
        assertNotNull(box);
        assertEquals(10, box.minX());
        assertEquals(15, box.maxX());
        assertEquals(60, box.minY());
        assertEquals(65, box.maxY());
        assertEquals(20, box.minZ());
        assertEquals(25, box.maxZ());
        assertEquals(6 * 6 * 6, box.getVolume());

        // Clear event
        WorldEditCuiHandler.handleMessage("e");
        assertFalse(WorldEditCuiHandler.hasSelection());
    }
}
