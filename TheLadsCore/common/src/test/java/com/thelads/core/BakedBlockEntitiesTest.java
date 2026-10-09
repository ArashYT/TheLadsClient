package com.thelads.core;

import static org.junit.jupiter.api.Assertions.*;

import com.thelads.core.client.ebe.BakedBlockEntities;
import com.thelads.core.client.ebe.BakedQuadOrder;
import java.util.GregorianCalendar;
import org.junit.jupiter.api.*;

class BakedBlockEntitiesTest {
    private static final long POS = 123456789L;

    @BeforeEach void reset() { BakedBlockEntities.clear(); }

    /** A chest opens: the mesh drops it first, the renderer takes over a frame after that build, and the reverse on close. */
    @Test void switchNeverShowsAGapOrADouble() {
        assertTrue(BakedBlockEntities.bake(POS), "untracked blocks are baked");
        assertFalse(BakedBlockEntities.drawDynamic(POS), "and their renderer is skipped");

        assertTrue(BakedBlockEntities.setAnimating(POS, true), "first open asks for a rebuild");
        assertFalse(BakedBlockEntities.setAnimating(POS, true), "but only once");
        assertTrue(BakedBlockEntities.animating(POS));
        assertFalse(BakedBlockEntities.drawDynamic(POS), "the old mesh still shows the baked chest");

        assertFalse(BakedBlockEntities.bake(POS), "the rebuild leaves it out");
        assertFalse(BakedBlockEntities.drawDynamic(POS), "not uploaded yet in the build frame");
        BakedBlockEntities.nextFrame();
        assertTrue(BakedBlockEntities.drawDynamic(POS), "renderer draws from the next frame");

        assertTrue(BakedBlockEntities.setAnimating(POS, false), "closing asks for a rebuild");
        assertFalse(BakedBlockEntities.setAnimating(POS, false));
        assertTrue(BakedBlockEntities.drawDynamic(POS), "renderer keeps drawing until the baked build lands");
        assertTrue(BakedBlockEntities.bake(POS));
        BakedBlockEntities.nextFrame();
        assertTrue(BakedBlockEntities.drawDynamic(POS), "upload may land a frame late");
        BakedBlockEntities.nextFrame();
        assertFalse(BakedBlockEntities.drawDynamic(POS), "baked mesh is on screen: renderer stops");
        assertFalse(BakedBlockEntities.animating(POS));
        assertTrue(BakedBlockEntities.bake(POS));
    }

    /** A build that raced a flip records what it actually meshed, so the renderer waits for the next one. */
    @Test void staleBuildDoesNotHandOver() {
        BakedBlockEntities.setAnimating(POS, true);
        BakedBlockEntities.bake(POS);
        BakedBlockEntities.nextFrame();
        assertTrue(BakedBlockEntities.drawDynamic(POS));
        BakedBlockEntities.setAnimating(POS, false);
        BakedBlockEntities.setAnimating(POS, true);
        assertFalse(BakedBlockEntities.drawDynamic(POS), "re-open before any build: wait for a fresh dynamic build");
        BakedBlockEntities.bake(POS);
        BakedBlockEntities.nextFrame();
        assertTrue(BakedBlockEntities.drawDynamic(POS));
    }

    @Test void christmasDates() {
        assertTrue(christmas(2026, 12, 24));
        assertTrue(christmas(2026, 12, 26));
        assertFalse(christmas(2026, 12, 23));
        assertFalse(christmas(2026, 12, 27));
        assertFalse(christmas(2026, 11, 25));
    }

    private static boolean christmas(int y, int m, int d) {
        try {
            var method = BakedBlockEntities.class.getDeclaredMethod("christmas", java.util.Calendar.class);
            method.setAccessible(true);
            return (Boolean) method.invoke(null, new GregorianCalendar(y, m - 1, d));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @Test void killSwitchAndUnknownMods() {
        System.setProperty("lads.ebe", "false");
        try {
            assertFalse(BakedBlockEntities.init());
            assertEquals("-Dlads.ebe=false", BakedBlockEntities.standDownReason());
        } finally {
            System.clearProperty("lads.ebe");
        }
        assertTrue(BakedBlockEntities.init(), "no loader on the test classpath: no similar mod");
        assertFalse(BakedBlockEntities.modLoaded("enhancedblockentities"));
    }

    // ---- quad order ----

    /** Vanilla's NORTH face (z = 0.0625) of a 14px box, in FaceInfo order. */
    private static final float[] NORTH = {
        0.9375f, 0.625f, 0.0625f,  0.9375f, 0f, 0.0625f,  0.0625f, 0f, 0.0625f,  0.0625f, 0.625f, 0.0625f};

    @Test void canonicalOrderSurvivesRotation() {
        assertEquals(2, BakedQuadOrder.direction(NORTH));
        assertArrayEquals(new int[] {0, 1, 2, 3}, BakedQuadOrder.order(NORTH, 2));
        float[] shifted = new float[12];
        for (int v = 0; v < 4; v++) System.arraycopy(NORTH, ((v + 1) % 4) * 3, shifted, v * 3, 3);
        assertEquals(2, BakedQuadOrder.direction(shifted), "cyclic shift keeps the winding");
        assertArrayEquals(new int[] {3, 0, 1, 2}, BakedQuadOrder.order(shifted, 2));
    }

    @Test void everyDirectionFromAUnitCube() {
        // FaceInfo corner order per face of the unit cube; each must map to itself.
        float[][] faces = {
            {0,0,1, 0,0,0, 1,0,0, 1,0,1}, {0,1,0, 0,1,1, 1,1,1, 1,1,0},
            {1,1,0, 1,0,0, 0,0,0, 0,1,0}, {0,1,1, 0,0,1, 1,0,1, 1,1,1},
            {0,1,0, 0,0,0, 0,0,1, 0,1,1}, {1,1,1, 1,0,1, 1,0,0, 1,1,0}};
        for (int d = 0; d < 6; d++) {
            assertEquals(d, BakedQuadOrder.direction(faces[d]), "direction " + d);
            assertArrayEquals(new int[] {0, 1, 2, 3}, BakedQuadOrder.order(faces[d], d), "order " + d);
        }
    }

    @Test void flatEdgesHaveNoArea() {
        float[] edge = {0,0,0, 0,1,0, 0,1,0, 0,0,0};
        assertEquals(0f, BakedQuadOrder.area2(edge), 1e-6);
        assertTrue(BakedQuadOrder.area2(NORTH) > 0.5f);
    }
}
