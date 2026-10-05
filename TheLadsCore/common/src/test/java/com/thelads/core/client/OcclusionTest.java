package com.thelads.core.client;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OcclusionTest {
    /** Opaque cells by coordinates. */
    private static final class Cells implements Occlusion.Grid {
        final Set<Long> cells = new HashSet<>();
        Cells box(int x0, int y0, int z0, int x1, int y1, int z1) {
            for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) cells.add(key(x, y, z));
            return this;
        }
        Cells air(int x, int y, int z) { cells.remove(key(x, y, z)); return this; }
        public boolean opaque(int x, int y, int z) { return cells.contains(key(x, y, z)); }
        static long key(int x, int y, int z) { return ((long) x & 0xFFFFF) << 40 | ((long) y & 0xFFFFF) << 20 | ((long) z & 0xFFFFF); }
    }

    private static boolean visible(Cells grid, double ex, double ey, double ez, double r, double... box) {
        double[] eyes = new double[27];
        int n = Occlusion.eyes(grid, ex, ey, ez, r, eyes);
        return n == 0 || Occlusion.visible(grid, eyes, n, box[0], box[1], box[2], box[3], box[4], box[5]);
    }

    // A player-sized box (with culling's margins) 10 blocks behind a wall at x = 5, the eye at x = 0.5.
    private static final double[] BEHIND = {14.5, 63.9, -0.5, 16.5, 66.5, 1.5};

    @Test void aWallHidesTheBoxAndAnyOpeningShowsIt() {
        Cells wall = new Cells().box(5, 60, -10, 5, 75, 10);
        assertTrue(visible(new Cells(), 0.5, 65.6, 0.5, 0.5, BEHIND), "open space");
        assertFalse(visible(wall, 0.5, 65.6, 0.5, 0.5, BEHIND), "a solid wall");
        assertTrue(visible(wall.air(5, 65, 0), 0.5, 65.6, 0.5, 0.5, BEHIND), "a one-block window");
    }

    @Test void peekingRoundACornerCountsTheWholeCubeAroundTheEye() {
        // The wall ends at z = 1 (cells z <= 0) right beside the eye, which stands 0.3 behind that end: half a block sideways
        // the line past the end reaches the box; from the eye the lines cross the wall's end cell deeper than DEPTH.
        Cells corner = new Cells().box(2, 60, -20, 2, 75, 0);
        double[] box = {20, 64, -0.8, 21, 66, -0.4};
        assertFalse(visible(corner, 0.5, 65.6, 0.7, 0, box), "hidden from the eye alone");
        assertTrue(visible(corner, 0.5, 65.6, 0.7, 0.5, box), "visible from a step inside the cube: not culled");
    }

    @Test void anEyeInsideAWallOrInsideTheBoxCullsNothing() {
        Cells wall = new Cells().box(5, 60, -10, 5, 75, 10);
        assertEquals(0, Occlusion.eyes(wall, 5.5, 65, 0.5, 0.5, new double[27]));
        assertTrue(visible(wall, 15.5, 65, 0.5, 0.5, BEHIND));
    }

    @Test void cornersInsideAnAdjacentWallArePulledIntoTheEyeCell() {
        // Standing against the wall: the cube's far corners are in it; they must not see through it.
        Cells wall = new Cells().box(1, 60, -10, 1, 75, 10);
        double[] eyes = new double[27];
        int n = Occlusion.eyes(wall, 0.7, 65.6, 0.5, 0.5, eyes);
        assertEquals(9, n);
        for (int i = 0; i < n; i++) assertTrue(eyes[i * 3] < 1, "eye sample " + i + " stays out of the wall");
        assertFalse(Occlusion.visible(wall, eyes, n, BEHIND[0], BEHIND[1], BEHIND[2], BEHIND[3], BEHIND[4], BEHIND[5]));
    }

    @Test void theWalkChecksEveryCellOnTheSegment() {
        Cells one = new Cells().box(3, 0, 3, 3, 0, 3);
        assertTrue(Occlusion.blocked(one, 0.5, 0.5, 0.5, 5.5, 0.5, 5.5), "diagonal through the cell's core");
        assertFalse(Occlusion.blocked(one, 0.5, 0.5, 0.5, 5.5, 0.5, 0.5), "beside it");
        assertFalse(Occlusion.blocked(one, 5.5, 0.5, 5.5, 3.9, 0.5, 3.9), "a lone block's edges are eroded: only its core blocks");
        assertTrue(Occlusion.blocked(new Cells().box(2, 0, 2, 4, 0, 4), 5.5, 0.5, 5.5, 3.5, 0.5, 3.5), "ending inside a slab");
        assertFalse(Occlusion.blocked(one, 3.5, 0.5, 3.5, 6.5, 0.5, 6.5), "starting inside it");
        assertFalse(Occlusion.blocked(one, 0.5, 0.5, 3.1, 5.5, 0.5, 3.1), "grazing its open side");
        assertTrue(Occlusion.blocked(new Cells().box(3, 0, 2, 3, 0, 4), 0.5, 0.5, 3.1, 5.5, 0.5, 3.1), "the same line through a wall");
        assertTrue(Occlusion.blocked(new Cells().box(-3, -1, -3, -3, -1, -3), -0.5, -0.5, -0.5, -5.5, -0.5, -5.5), "negative coordinates");
    }

    @Test void theCameraIsWhereTheModelviewMapsTheOrigin() {
        // Pitch 30, yaw 120, then the world moved by -(2, 1.62, -3): the camera stands at (2, 1.62, -3).
        double p = Math.toRadians(30), y = Math.toRadians(120);
        double[][] pitch = {{1, 0, 0}, {0, Math.cos(p), -Math.sin(p)}, {0, Math.sin(p), Math.cos(p)}};
        double[][] yaw = {{Math.cos(y), 0, Math.sin(y)}, {0, 1, 0}, {-Math.sin(y), 0, Math.cos(y)}};
        double[] cam = {2, 1.62, -3};
        float[] m = new float[16];
        for (int i = 0; i < 3; i++) {
            double t = 0;
            for (int j = 0; j < 3; j++) {
                double r = 0;
                for (int k = 0; k < 3; k++) r += pitch[i][k] * yaw[k][j];
                m[j * 4 + i] = (float) r;
                t -= r * cam[j];
            }
            m[12 + i] = (float) t;
        }
        m[15] = 1;
        double[] out = new double[3];
        assertTrue(Occlusion.cameraOf(m, out));
        for (int i = 0; i < 3; i++) assertEquals(cam[i], out[i], 1e-5);
        assertFalse(Occlusion.cameraOf(new float[16], out));
    }

    @Test void anItemIsNeverHiddenByTheFloorItRestsOn() {
        // A flat floor (cells y <= 3); items resting on it at 2.2 to 6 blocks, also sunk 0.05 into it (Item Physics lays them
        // flat), seen standing (eye 1.62 up) and sneaking (1.54), with and without culling's margins, from all round.
        Cells floor = new Cells().box(-20, 0, -20, 20, 3, 20);
        for (double distance : new double[] {2.2, 2.8, 3.5, 5, 6})
            for (double sink : new double[] {0, 0.05})
                for (double eyeY : new double[] {4 + 1.62, 4 + 1.54})
                    for (int angle = 0; angle < 360; angle += 45) {
                        double x = 0.5 + distance * Math.cos(Math.toRadians(angle)), z = 0.5 + distance * Math.sin(Math.toRadians(angle));
                        double[] raw = {x - 0.125, 4 - sink, z - 0.125, x + 0.125, 4.25 - sink, z + 0.125};
                        double[] margins = {raw[0] - 0.75, raw[1] - 0.1, raw[2] - 0.75, raw[3] + 0.75, raw[4] + 0.75, raw[5] + 0.75};
                        String at = "item " + distance + " away at " + angle + " degrees, sunk " + sink + ", eye " + eyeY;
                        assertTrue(visible(floor, 0.5, eyeY, 0.5, 0.5, raw), at);
                        assertTrue(visible(floor, 0.5, eyeY, 0.5, 0.5, margins), at + ", with margins");
                        assertTrue(visible(floor, 0.5, eyeY, 0.5, 0, raw), at + ", from the eye alone");
                    }
    }

    @Test void anItemOnALedgeAboveTheEyeIsSeenOverItsEdgeOnly() {
        // A 4-high ledge (cells y 4..7, x >= 3, top at 8) above a standing eye (5.62): an item at its edge is seen over the edge;
        // one deep on top is not. (Close under a thin layer's surface the eroded edges let grazing lines through: drawn, never hidden.)
        Cells ledge = new Cells().box(-20, 0, -20, 20, 3, 20).box(3, 4, -20, 20, 7, 20);
        assertTrue(visible(ledge, 0.5, 5.62, 0.5, 0.5, 3.0, 8.0, 0.4, 3.25, 8.25, 0.6), "at the edge");
        assertFalse(visible(ledge, 0.5, 5.62, 0.5, 0.5, 9.0, 8.0, 0.4, 9.25, 8.25, 0.6), "six blocks in");
    }
}
