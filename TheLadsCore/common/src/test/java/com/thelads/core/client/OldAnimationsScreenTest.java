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
 * Targets: 1.7.10 renderItemInFirstPerson and its 2D icon (over [0,1]², mirrored in x, z -1/16..0) projected at a 70°
 * vertical FOV into 1280x720, right hand, raised, no swing.
 */
class OldAnimationsScreenTest {
    private static final double TAN = Math.tan(Math.toRadians(35)), ASPECT = 1280 / 720.0;

    /** Texel corner (u, v) of the modern NONE draw through the chain, in pixels. */
    private static double[] pixel(Mat chain, double u, double v) {
        double[] p = chain.point(u - 0.5, 0.5 - v, 0);
        return new double[]{640 + p[0] / -p[2] / (TAN * ASPECT) * 640, 360 - p[1] / -p[2] / TAN * 360};
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

    @Test void firstPersonLandsOn1710Pixels() {
        assertPixels(firstPerson(Use.NONE, 0, 1), 1.5, "idle sword", new double[]{0, 1, 1017, 842}, new double[]{1, 0, 1289, 236});
        assertPixels(firstPerson(Use.BLOCK, 0, 1), 1.5, "sword block", new double[]{0, 1, 1214, 841}, new double[]{1, 0, 566, 407},
            new double[]{1, 1, 966, 313}, new double[]{0, 0, 633, 911});
        // Full draw, standing nearly upright at x 900-1000: not edge-on, not aimed at the crosshair.
        assertPixels(firstPerson(Use.BOW, 72000 - 30, 72000), 1.5, "bow at full draw", new double[]{0, 1, 917, 880},
            new double[]{1, 0, 1018, -21}, new double[]{0, 0, 836, 399});
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
            for (double u = 0; u <= 1; u++)
                for (double v = 0; v <= 1; v++)
                    assertArrayEquals(legacy.point(1 - u, 1 - v, -1 / 32.0), modern.point(u - 0.5, 0.5 - v, 0), 1e-4,
                        "third-person sword" + (blocking ? " block" : "") + ", texture corner (" + u + ", " + v + ")");
        }
    }
}
