package com.thelads.core.client;

import com.thelads.core.client.OldAnimations.Held;
import com.thelads.core.client.OldAnimations.Use;
import com.thelads.core.client.OldAnimationsTest.Mat;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The modern chain end to end against 1.7.10 on screen. Modern: the arm origin (the matrix at renderArmWithItem's push,
 * bobbing aside), the recipes, then how 1.21.x and 26.x draw a flat item with ItemDisplayContext.NONE: no display
 * transform, the renderer's -0.5 translate, the generated mesh over [0,1]² reading the texture from +Z (mid-plane z 0.5).
 * Targets: 1.7.10 renderItemInFirstPerson and its 2D icon (over [0,1]², mirrored in x, z -1/16..0) at half size about the
 * icon's centre (OldAnimations: the size players know), projected at a 70° vertical FOV into 1280x720, right hand, raised,
 * no swing.
 */
class OldAnimationsScreenTest {
    private static final double TAN = Math.tan(Math.toRadians(35)), ASPECT = 1280 / 720.0;

    /** Texel corner (u, v) of the modern NONE draw through the chain, in pixels. */
    private static double[] pixel(Mat chain, double u, double v) {
        return project(chain.point(u - 0.5, 0.5 - v, 0));
    }

    private static Mat firstPerson(Use use, int remaining, int maxUse) {
        Mat chain = new Mat();
        OldAnimations.hand(chain, 1, 0, 0, use, remaining, 0, maxUse);
        OldAnimations.item(chain, 1, false);
        return chain;
    }

    /** {u, v, x, y}: texture corner and its 1.7.10 pixel. */
    private static void assertPixels(Mat chain, double tolerance, String what, double[]... corners) {
        for (double[] corner : corners)
            assertArrayEquals(new double[]{corner[2], corner[3]}, pixel(chain, corner[0], corner[1]), tolerance,
                what + ", texture corner (" + corner[0] + ", " + corner[1] + ")");
    }

    @Test void firstPersonLandsOn1710PlaceAtHalfSize() {
        assertPixels(firstPerson(Use.NONE, 0, 1), 1.5, "idle sword", new double[]{0, 1, 1063, 740}, new double[]{1, 0, 1193, 451},
            new double[]{0.5, 0.5, 1120, 613});
        assertPixels(firstPerson(Use.BLOCK, 0, 1), 1.5, "sword block", new double[]{0, 1, 992, 693}, new double[]{1, 0, 680, 483},
            new double[]{1, 1, 897, 437}, new double[]{0, 0, 732, 733});
        // Full draw, standing nearly upright at x 880-990: not edge-on, not aimed at the crosshair.
        assertPixels(firstPerson(Use.BOW, 72000 - 30, 72000), 1.5, "bow at full draw", new double[]{0, 1, 942, 658},
            new double[]{1, 0, 993, 208}, new double[]{0, 0, 879, 411});
    }

    /** The user's report: 1.7 items drawn at 1.7.10's full icon size looked far too big. Idle, they are about 1.8.9's own. */
    @Test void idleSwordIsAbout189Size() {
        // 1.8.9: transformFirstPersonItem, the sword's firstperson display transform, RenderItem's 0.5, the mesh over [0,1]².
        Mat vanilla = new Mat().t(0.56, -0.52, -0.72).ry(45).s(0.4).t(0, 0.25, 0.125).ry(-135).rz(25).s(1.7).s(0.5).t(-0.5, -0.5, 0);
        double[] handle = project(vanilla.point(0, 0, 0)), tip = project(vanilla.point(1, 1, 0));
        Mat ours = firstPerson(Use.NONE, 0, 1);
        double ratio = length(pixel(ours, 0, 1), pixel(ours, 1, 0)) / length(handle, tip);
        assertTrue(ratio > 0.9 && ratio < 1.25, "idle sword on screen vs 1.8.9's: " + ratio);
    }

    private static double[] project(double[] p) {
        return new double[]{640 + p[0] / -p[2] / (TAN * ASPECT) * 640, 360 - p[1] / -p[2] / TAN * 360};
    }

    private static double length(double[] a, double[] b) {
        return Math.hypot(a[0] - b[0], a[1] - b[1]);
    }

    @Test void thirdPersonUndoesTheModernLayerAndMatches1710RenderPlayer() {
        for (boolean blocking : new boolean[]{false, true}) {
            // Both start where the arm transform leaves the matrix (1.7.10 postRender, modern translateToHand: same ops).
            // Modern: ItemInHandLayer's -90° X, 180° Y and (1, 2, -10)/16, then the adapters' undo of exactly those.
            Mat modern = new Mat().rx(-90).ry(180).t(1 / 16.0, 0.125, -0.625).t(-1 / 16.0, -0.125, 0.625).ry(-180).rx(90);
            OldAnimations.thirdPersonHand(modern, 1);
            OldAnimations.thirdPersonItem(modern, 1, Held.TOOL, blocking);
            Mat legacy = new Mat().t(-0.0625, 0.4375, 0.0625);
            if (blocking) legacy.t(0.05, 0, -0.1).ry(-50).rx(-10).rz(-60);
            legacy.t(0, 0.1875, 0).s(0.625, -0.625, 0.625).rx(-100).ry(45).t(0, -0.3, 0).s(1.5).ry(50).rz(335).t(-0.9375, -0.0625, 0);
            // 1.7.10's icon corner (1 - u, 1 - v), pulled halfway to the icon's centre.
            for (double u = 0; u <= 1; u++)
                for (double v = 0; v <= 1; v++)
                    assertArrayEquals(legacy.point(0.75 - u / 2, 0.75 - v / 2, -1 / 32.0), modern.point(u - 0.5, 0.5 - v, 0), 1e-4,
                        "third-person sword" + (blocking ? " block" : "") + ", texture corner (" + u + ", " + v + ")");
        }
    }
}
