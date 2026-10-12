package com.thelads.core.client;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LayerVoxelsTest {
    /** A skin of 64 x 64 transparent pixels but those listed (x, y pairs). */
    private static int[] skin(int... pixels) {
        int[] argb = new int[64 * 64];
        for (int i = 0; i < pixels.length; i += 2) argb[pixels[i + 1] * 64 + pixels[i]] = 0xFFFF8800;
        return argb;
    }

    /** A face in the plane z = 0, 8 x 8 model units from the origin, showing skin pixels 8..16 x 8..16, u growing with x and v with y. */
    private static float[][] face(float u0, float u1) {
        return new float[][] {{0, 0, 0, u0, 8}, {8, 0, 0, u1, 8}, {8, 8, 0, u1, 16}, {0, 8, 0, u0, 16}};
    }

    @Test void aPixelBecomesABoxStandingOutOfTheFace() {
        List<LayerVoxels.Box> boxes = new ArrayList<>();
        LayerVoxels.extrude(face(8, 16), new float[] {4, 4, 5}, skin(9, 9, 10, 9), 64, 64, boxes); // the box lies at z > 0: outside is -z
        assertEquals(2, boxes.size());
        var first = boxes.get(0);
        assertEquals(9, first.px());
        assertEquals(9, first.py());
        assertEquals(1f, first.x(), 1e-4);
        assertEquals(1f, first.dx(), 1e-4);
        assertEquals(1f, first.y(), 1e-4);
        assertEquals(1f, first.dy(), 1e-4);
        assertEquals(-LayerVoxels.OUT, first.z(), 1e-4);
        assertEquals(LayerVoxels.OUT, first.dz(), 1e-4);
        assertEquals(2f, boxes.get(1).x(), 1e-4, "the next pixel is the next unit");
    }

    @Test void theOutsideIsTheSideAwayFromTheBoxCenter() {
        List<LayerVoxels.Box> boxes = new ArrayList<>();
        LayerVoxels.extrude(face(8, 16), new float[] {4, 4, -5}, skin(9, 9), 64, 64, boxes);
        assertEquals(0f, boxes.get(0).z(), 1e-4);
        assertEquals(LayerVoxels.OUT, boxes.get(0).dz(), 1e-4);
    }

    @Test void aMirroredFaceShowsItsPixelsTheOtherWayRound() {
        List<LayerVoxels.Box> boxes = new ArrayList<>();
        LayerVoxels.extrude(face(16, 8), new float[] {4, 4, 5}, skin(9, 9), 64, 64, boxes); // u shrinks with x: pixel 9 is near x = 8
        assertEquals(6f, boxes.get(0).x(), 1e-4);
    }

    @Test void transparentPixelsAndPixelsOutsideTheFaceMakeNoBox() {
        List<LayerVoxels.Box> boxes = new ArrayList<>();
        int[] almostClear = skin();
        almostClear[9 * 64 + 9] = 0x0F123456; // alpha 15
        LayerVoxels.extrude(face(8, 16), new float[] {4, 4, 5}, almostClear, 64, 64, boxes);
        LayerVoxels.extrude(face(8, 16), new float[] {4, 4, 5}, skin(20, 20, 7, 9), 64, 64, boxes);
        assertTrue(boxes.isEmpty());
    }

    @Test void aFaceAcrossTheSkinEdgeIsClipped() {
        List<LayerVoxels.Box> boxes = new ArrayList<>();
        float[][] edge = {{0, 0, 0, 60, 60}, {8, 0, 0, 68, 60}, {8, 8, 0, 68, 68}, {0, 8, 0, 60, 68}};
        LayerVoxels.extrude(edge, new float[] {4, 4, 5}, skin(63, 63), 64, 64, boxes);
        assertEquals(1, boxes.size());
    }
}
