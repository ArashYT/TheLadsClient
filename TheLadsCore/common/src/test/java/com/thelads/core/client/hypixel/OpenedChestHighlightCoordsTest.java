package com.thelads.core.client.hypixel;

import static org.junit.jupiter.api.Assertions.*;

import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.client.bridge.DefaultGameBridge;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

public class OpenedChestHighlightCoordsTest {
    private TestBridge bridge;

    private static class TestBridge extends DefaultGameBridge {
        @Override
        public String getServerAddress() {
            return "mc.hypixel.net";
        }

        @Override
        public boolean isIngame() {
            return true;
        }
    }

    @BeforeEach
    void setUp() {
        bridge = new TestBridge();
        LadsGameBridge.set(bridge);
        HypixelSafetyManager.qaOverride = true;
        HypixelMods.resetForTest();

        Module m = ModuleManager.getInstance().getModule(HypixelMods.HYPIXEL_MODS);
        if (m != null) {
            m.setEnabled(true);
            if (m.getOption(HypixelMods.OPENED_CHESTS) instanceof BoolOption b) {
                b.set(true);
            }
        }
        OpenedChestHighlight.clear();
    }

    @AfterEach
    void tearDown() {
        OpenedChestHighlight.clear();
        HypixelSafetyManager.qaOverride = false;
        HypixelMods.resetForTest();
        RealPing.tick(new DefaultGameBridge(), null, 0L);
        LadsGameBridge.set(new DefaultGameBridge());
    }

    @Test
    void testCoordinatePackingAndUnpacking() {
        int[][] testCoords = {
            {0, 64, 0},
            {10, 65, -20},
            {-150, 70, 300},
            {-5000, -32, -12000},
            {123456, 128, -654321}
        };

        for (int[] coord : testCoords) {
            int x = coord[0], y = coord[1], z = coord[2];
            // Test that recordChestOpened marks the exact coordinates
            OpenedChestHighlight.recordChestOpened(x, y, z);
            // Verify coordinate extraction
            AtomicInteger count = new AtomicInteger();
            OpenedChestHighlight.forEachOpened((ux, uy, uz) -> {
                if (ux == x && uy == y && uz == z) count.incrementAndGet();
            });
            assertEquals(1, count.get(), "Coordinate " + x + "," + y + "," + z + " must unpack accurately");
            OpenedChestHighlight.clear();
        }
    }
}
