package com.thelads.core.perf;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;
import org.junit.jupiter.api.Test;

class FaceCullTest {
    /** A GUI-like orthographic matrix: x right, y down (flipped), w = 1, scaled to a 1920x1080 viewport. */
    private static float[] guiOrtho() {
        float[] m = new float[FaceCull.MATRIX_FLOATS];
        m[0] = 2f / 960; m[5] = -2f / 540; m[10] = -0.001f; m[12] = -1; m[13] = 1; m[15] = 1;
        return FaceCull.prepare(FaceCull.toViewport(m, 1920, 1080));
    }

    /** A perspective camera at the origin looking down -z (90 degree FOV, square), scaled to 1000x1000 pixels. */
    private static float[] perspective() {
        float[] m = new float[FaceCull.MATRIX_FLOATS];
        m[0] = 1; m[5] = 1; m[10] = -1.002f; m[11] = -1; m[14] = -0.2f;
        return FaceCull.prepare(FaceCull.toViewport(m, 1000, 1000));
    }

    private static int test(float[] m, float[] q, float lod) {
        return FaceCull.test(m, q[0], q[1], q[2], q[3], q[4], q[5], q[6], q[7], q[8], q[9], q[10], q[11], lod);
    }

    private static int projected(float[] m, float[] q, float lod) {
        return FaceCull.projected(m, q[0], q[1], q[2], q[3], q[4], q[5], q[6], q[7], q[8], q[9], q[10], q[11], lod);
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
        float e = 1e-7f; // a wall in the plane x = 1e-7, almost through the eye: barely back-facing, within the tolerance
        float[] edgeOn = {e, -1, -6, e, -1, -4, e, 1, -4, e, 1, -6};
        assertEquals(FaceCull.KEEP, test(m, edgeOn, 0));
        float[] crossingNearPlane = {-1, -1, 1, -1, 1, 1, 1, 1, -5, 1, -1, -5};
        assertEquals(FaceCull.KEEP, test(m, crossingNearPlane, 0));
    }

    @Test void bentAndSelfCrossingQuadsTakeTheFullTest() {
        float[] m = guiOrtho();
        // a bow tie on screen: one diagonal split gives a front and a back triangle
        float[] bowTie = {100, 100, 4, 116, 116, 4, 116, 100, 4, 100, 116, 4};
        assertEquals(FaceCull.KEEP, test(m, bowTie, 0));
        float[] bent = {100, 100, 4, 116, 100, 9, 116, 116, 4, 100, 116, 4}; // one corner lifted out of the plane
        assertEquals(projected(m, bent, 0), test(m, bent, 0));
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

    /** The one-dot-product test agrees with projecting the corners for random planar quads seen through random cameras. */
    @Test void fastTestAgreesWithTheProjectedOne() {
        Random random = new Random(185);
        int compared = 0, culled = 0;
        for (int i = 0; i < 20000; i++) {
            float[] m = new float[FaceCull.MATRIX_FLOATS];
            boolean ortho = random.nextBoolean();
            // projection x random rotation/scale/mirror x translation in front of the camera
            float[] l = new float[9];
            for (int k = 0; k < 9; k++) l[k] = random.nextFloat() * 2 - 1;
            float tx = random.nextFloat() * 2 - 1, ty = random.nextFloat() * 2 - 1, tz = -3 - random.nextFloat() * 20;
            float sx = 300 * (random.nextBoolean() ? 1 : -1), sy = 300 * (random.nextBoolean() ? 1 : -1);
            for (int col = 0; col < 3; col++) {
                float vx = l[col * 3], vy = l[col * 3 + 1], vz = l[col * 3 + 2];
                if (ortho) { m[col * 4] = sx * vx; m[col * 4 + 1] = sy * vy; m[col * 4 + 2] = vz; m[col * 4 + 3] = 0; }
                else { m[col * 4] = sx * vx; m[col * 4 + 1] = sy * vy; m[col * 4 + 2] = vz; m[col * 4 + 3] = -vz; }
            }
            if (ortho) { m[12] = sx * tx; m[13] = sy * ty; m[14] = tz; m[15] = 1; }
            else { m[12] = sx * tx; m[13] = sy * ty; m[14] = tz; m[15] = -tz; }
            FaceCull.prepare(m);
            // a planar quad: a parallelogram around a random point
            float ox = random.nextFloat() - 0.5f, oy = random.nextFloat() - 0.5f, oz = random.nextFloat() - 0.5f;
            float ax = random.nextFloat() - 0.5f, ay = random.nextFloat() - 0.5f, az = random.nextFloat() - 0.5f;
            float bx = random.nextFloat() - 0.5f, by = random.nextFloat() - 0.5f, bz = random.nextFloat() - 0.5f;
            float[] q = {ox, oy, oz, ox + ax, oy + ay, oz + az, ox + ax + bx, oy + ay + by, oz + az + bz, ox + bx, oy + by, oz + bz};
            int full = projected(m, q, 0);
            if (full == FaceCull.KEEP && nearlyEdgeOn(m, q)) continue; // the tolerance zone, where both may keep or not
            assertEquals(full, test(m, q, 0), "quad " + i);
            compared++;
            if (full == FaceCull.CULL) culled++;
        }
        assertTrue(compared > 15000 && culled > 5000, compared + " compared, " + culled + " culled");
    }

    /** Within a thousand times the tolerance of edge-on on screen. */
    private static boolean nearlyEdgeOn(float[] m, float[] q) {
        float[] x = new float[4], y = new float[4];
        for (int v = 0; v < 4; v++) {
            float w = m[3] * q[v * 3] + m[7] * q[v * 3 + 1] + m[11] * q[v * 3 + 2] + m[15];
            x[v] = (m[0] * q[v * 3] + m[4] * q[v * 3 + 1] + m[8] * q[v * 3 + 2] + m[12]) / w;
            y[v] = (m[1] * q[v * 3] + m[5] * q[v * 3 + 1] + m[9] * q[v * 3 + 2] + m[13]) / w;
        }
        float p = (x[2] - x[0]) * (y[3] - y[1]), r = (y[2] - y[0]) * (x[3] - x[1]);
        return Math.abs(p - r) < 0.2f * (Math.abs(p) + Math.abs(r));
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
