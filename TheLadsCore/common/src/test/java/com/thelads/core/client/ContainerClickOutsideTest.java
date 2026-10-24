package com.thelads.core.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContainerClickOutsideTest {
    @Test
    void testClickOutsideDetection() {
        int left = 100, top = 50, xSize = 176, ySize = 166;
        // Inside
        assertFalse(ContainerQoL.isClickOutside(100, 50, left, top, xSize, ySize));
        assertFalse(ContainerQoL.isClickOutside(150, 100, left, top, xSize, ySize));
        assertFalse(ContainerQoL.isClickOutside(275, 215, left, top, xSize, ySize));

        // Outside left
        assertTrue(ContainerQoL.isClickOutside(99, 100, left, top, xSize, ySize));
        // Outside right
        assertTrue(ContainerQoL.isClickOutside(276, 100, left, top, xSize, ySize));
        // Outside top
        assertTrue(ContainerQoL.isClickOutside(150, 49, left, top, xSize, ySize));
        // Outside bottom
        assertTrue(ContainerQoL.isClickOutside(150, 216, left, top, xSize, ySize));
    }
}
