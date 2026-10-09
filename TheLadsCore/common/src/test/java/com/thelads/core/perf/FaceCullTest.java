package com.thelads.core.perf;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class FaceCullTest {
    /** A GUI-like orthographic matrix: x right, y down (flipped), w = 1, scaled to a 1920x1080 viewport. */
    private static float[] guiOrtho() {
        float[] m = new float[16];
        m[0] = 2f / 960; m[5] = -2f / 540; m[10] = -0.001f; m[12] = -1; m[13] = 1; m[15] = 1;
        return FaceCull.toViewport(m, 1920, 1080);
    }

    /** A perspective camera at the origin looking down -z (90 degree FOV, square), scaled to 1000x1000 pixels. */
    private static float[] perspective() {
        float[] m = new float[16];
        m[0] = 1; m[5] = 1; m[10] = -1.002f; m[11] = -1; m[14] = -0.2f;
        return FaceCull.toViewport(m, 1000, 1000);
    }

    private static int test(float[] m, float[] q, float lod) {
        return FaceCull.test(m, q[0], q[1], q[2], q[3], q[4], q[5], q[6], q[7], q[8], q[9], q[10], q[11], lod);
    }

    @Test void guiFlatItemKeepsOnlyItsFront() {
        float[] m = guiOrtho();
        // y is flipped on screen, so the quad that is clockwise in GUI space is counter-clockwise on screen: the front
        float[] front = {100, 100, 5, 100, 116, 5, 116, 116, 5, 116, 100, 5};
        float[] back = {100, 100, 4, 116, 100, 4, 116, 116, 4, 100, 116, 4};
        float[] edge = {100, 108, 4, 108, 108, 4, 108, 108, 5, 100, 108, 5}; // a pixel strip's top: no area on screen
        assertEquals(FaceCull.KEEP, test(m, front, 0));
        assertEquals(FaceCull.CULL, test(m, back, 0));
        assertEquals(FaceCull.CULL, test(m, edge, 0));
    }

    @Test void perspectiveCullsWhatFacesAway() {
        float[] m = perspective();
        float[] facing = {-1, -1, -5, 1, -1, -5, 1, 1, -5, -1, 1, -5};   // counter-clockwise seen from the camera
        float[] away = {-1, -1, -5, -1, 1, -5, 1, 1, -5, 1, -1, -5};
        assertEquals(FaceCull.KEEP, test(m, facing, 0));
        assertEquals(FaceCull.CULL, test(m, away, 0));
        // a side wall of a box in front of the camera: its outside faces away only from one side
        float[] leftWallOutside = {-1, -1, -6, -1, -1, -4, -1, 1, -4, -1, 1, -6};
        float[] leftWallInside = {-1, -1, -6, -1, 1, -6, -1, 1, -4, -1, -1, -4};
        assertEquals(FaceCull.KEEP, test(m, leftWallInside, 0));
        assertEquals(FaceCull.CULL, test(m, leftWallOutside, 0));
    }

    @Test void nearlyEdgeOnAndBehindTheCameraAreLeftToTheGpu() {
        float[] m = perspective();
        // a wall in the plane x = 1e-6, almost through the eye: barely back-facing, within the tolerance
        float e = 1e-7f;
        float[] edgeOn = {e, -1, -6, e, -1, -4, e, 1, -4, e, 1, -6};
        assertEquals(FaceCull.KEEP, test(m, edgeOn, 0));
        float[] crossingNearPlane = {-1, -1, 1, -1, 1, 1, 1, 1, -5, 1, -1, -5};
        assertEquals(FaceCull.KEEP, test(m, crossingNearPlane, 0));
    }

    @Test void bentQuadIsCulledOnlyWhenEverySplitFacesAway() {
        float[] m = guiOrtho();
        // a bow tie on screen: one diagonal split gives a front and a back triangle
        float[] bowTie = {100, 100, 4, 116, 116, 4, 116, 100, 4, 100, 116, 4};
        assertEquals(FaceCull.KEEP, test(m, bowTie, 0));
    }

    @Test void lodDropsOnlyFrontQuadsSmallerThanTheLimit() {
        float[] m = perspective();
        float s = 0.0005f; // about 0.2 px wide at 5 blocks in a 1000 px view
        float[] tiny = {-s, -s, -5, s, -s, -5, s, s, -5, -s, s, -5};
        float[] big = {-1, -1, -5, 1, -1, -5, 1, 1, -5, -1, 1, -5};
        assertEquals(FaceCull.LOD, test(m, tiny, 0.25f));
        assertEquals(FaceCull.KEEP, test(m, tiny, 0));
        assertEquals(FaceCull.KEEP, test(m, big, 0.25f));
    }

    @Test void packedVerticesUseTheirStride() {
        float[] m = guiOrtho();
        float[] back = {100, 100, 4, 116, 100, 4, 116, 116, 4, 100, 116, 4};
        int stride = 8;
        int[] packed = new int[4 * stride];
        for (int v = 0; v < 4; v++) for (int c = 0; c < 3; c++) packed[v * stride + c] = Float.floatToRawIntBits(back[v * 3 + c]);
        assertEquals(FaceCull.CULL, FaceCull.test(m, packed, stride, 0));
    }

    @Test void clipMultipliesColumnMajor() {
        float[] translate = new float[16], scale = new float[16], out = new float[16];
        translate[0] = translate[5] = translate[10] = translate[15] = 1; translate[12] = 3;
        scale[0] = 2; scale[5] = 2; scale[10] = 2; scale[15] = 1;
        FaceCull.clip(out, translate, scale, 2, 2); // viewport 2x2: scale 1
        assertEquals(2, out[0]); assertEquals(3, out[12]);
    }

    @Test void killSwitchAndMissingModsDecide() {
        assertTrue(BuiltInRender.switchedOff("false"));
        assertTrue(BuiltInRender.switchedOff(" OFF "));
        assertFalse(BuiltInRender.switchedOff("true"));
        assertEquals("-Dlads.kerria=false", BuiltInRender.decide("kerria", "false", "kerria"));
        assertNull(BuiltInRender.decide("kerria", null, "kerria"));   // no loader on the test classpath: nothing installed
        assertFalse(BuiltInRender.modPresent("flerovium"));
    }
}
