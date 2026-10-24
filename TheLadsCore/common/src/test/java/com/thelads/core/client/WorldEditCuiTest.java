package com.thelads.core.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thelads.core.client.worldedit.WorldEditCUI;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorldEditCuiTest {

    @BeforeEach
    void setUp() {
        WorldEditCUI.clear();
        WorldEditCUI.on = true;
    }

    @Test
    void parseCuboidPointsAndBounds() {
        WorldEditCUI.parseCommand("s|cuboid");
        assertEquals(WorldEditCUI.Shape.CUBOID, WorldEditCUI.getShape());

        WorldEditCUI.parseCommand("p|0|10|20|30|0");
        assertNotNull(WorldEditCUI.getPos1());
        assertEquals(10, WorldEditCUI.getPos1().x());
        assertEquals(20, WorldEditCUI.getPos1().y());
        assertEquals(30, WorldEditCUI.getPos1().z());

        WorldEditCUI.parseCommand("p|1|15|25|35|0");
        assertNotNull(WorldEditCUI.getPos2());
        assertEquals(15, WorldEditCUI.getPos2().x());
        assertEquals(25, WorldEditCUI.getPos2().y());
        assertEquals(35, WorldEditCUI.getPos2().z());
        assertTrue(WorldEditCUI.hasSelection());
    }

    @Test
    void parseExpandCommand() {
        WorldEditCUI.parseCommand("e|5|10|15|20|30|40");
        assertEquals(WorldEditCUI.Shape.CUBOID, WorldEditCUI.getShape());
        assertEquals(5, WorldEditCUI.getPos1().x());
        assertEquals(20, WorldEditCUI.getPos2().x());
    }

    @Test
    void clearCommandClearsSelection() {
        WorldEditCUI.parseCommand("s|cuboid");
        WorldEditCUI.parseCommand("p|0|1|2|3|0");
        assertTrue(WorldEditCUI.hasSelection());

        WorldEditCUI.parseCommand("clr");
        assertFalse(WorldEditCUI.hasSelection());
        assertEquals(WorldEditCUI.Shape.NONE, WorldEditCUI.getShape());
    }

    @Test
    void drawingEmitsWireframeLines() {
        WorldEditCUI.parseCommand("s|cuboid");
        WorldEditCUI.parseCommand("p|0|0|0|0|0");
        WorldEditCUI.parseCommand("p|1|2|2|2|0");

        AtomicInteger lineCount = new AtomicInteger(0);
        WorldEditCUI.draw((x0, y0, z0, x1, y1, z1, argb) -> lineCount.incrementAndGet());

        assertTrue(lineCount.get() >= 12, "Should emit at least 12 bounding box edges");
    }

    @Test
    void safetyManagerGatesWorldEditCui() {
        assertTrue(HypixelSafetyManager.isRiskyModule(WorldEditCUI.NAME), "WorldEdit CUI must be gated as risky under anti-ban rules");
    }
}
