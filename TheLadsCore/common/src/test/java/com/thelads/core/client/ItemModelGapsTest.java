package com.thelads.core.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ItemModelGapsTest {
    @Test
    void anEdgeQuadSitsInsideItsPixelAndOverlapsTheFacesAndItsNeighbours() {
        // The left edge of the pixel column x 3-4 (outward normal -x), rows y 10-9 as 26.x writes them (from above to).
        float[] from = {3, 10, 7.5f}, to = {3, 9, 8.5f};
        ItemModelGaps.overlap(from, to, -1, 0);
        assertTrue(from[0] > 3 && to[0] == from[0], "inside the opaque pixel, still a flat quad");
        assertTrue(from[1] > 10 && to[1] < 9, "longer at both ends, whichever corner is the higher");
        assertTrue(from[2] < 7.5f && to[2] > 8.5f, "past the front and back faces");
        assertTrue(from[0] - 3 < 0.01f && from[2] > 7.49f, "by under 1/100 of a pixel");

        float[] top = {5, 12, 7.5f}, topTo = {6, 12, 8.5f}; // a top edge, normal +y
        ItemModelGaps.overlap(top, topTo, 0, 1);
        assertTrue(top[1] < 12 && topTo[1] == top[1] && top[0] < 5 && topTo[0] > 6);
    }
}
