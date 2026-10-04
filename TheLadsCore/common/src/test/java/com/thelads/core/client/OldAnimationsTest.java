package com.thelads.core.client;

import com.thelads.core.client.OldAnimations.Held;
import com.thelads.core.client.OldAnimations.Use;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Expected chains are written from Minecraft 1.7.10's first-person, third-person and dropped-item rendering. */
class OldAnimationsTest {
    private static final double EPS = 2e-4;
    private static final int FOOD = 32, BOW = 72000;
    private static final float[] SWINGS = {0, 0.5f, 1};

    /** Composes ops like GL and PoseStack: each op post-multiplies. Chainable double versions write expected chains. */
    static final class Mat implements OldAnimations.Sink {
        double[] m = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};

        @Override public void translate(float x, float y, float z) { t(x, y, z); }
        @Override public void rotate(float degrees, float x, float y, float z) {
            assertEquals(1, Math.abs(x) + Math.abs(y) + Math.abs(z), 0, "recipes rotate about unit X, Y or Z axes");
            r(degrees, x, y, z);
        }
        @Override public void scale(float x, float y, float z) { s(x, y, z); }

        Mat t(double x, double y, double z) { return mul(1, 0, 0, x, 0, 1, 0, y, 0, 0, 1, z, 0, 0, 0, 1); }
        Mat s(double x, double y, double z) { return mul(x, 0, 0, 0, 0, y, 0, 0, 0, 0, z, 0, 0, 0, 0, 1); }
        Mat s(double f) { return s(f, f, f); }
        Mat rx(double degrees) { return r(degrees, 1, 0, 0); }
        Mat ry(double degrees) { return r(degrees, 0, 1, 0); }
        Mat rz(double degrees) { return r(degrees, 0, 0, 1); }
        Mat r(double degrees, double x, double y, double z) {
            double a = Math.toRadians(degrees), c = Math.cos(a), s = Math.sin(a), k = 1 - c;
            return mul(c + x * x * k, x * y * k - z * s, x * z * k + y * s, 0,
                y * x * k + z * s, c + y * y * k, y * z * k - x * s, 0,
                z * x * k - y * s, z * y * k + x * s, c + z * z * k, 0, 0, 0, 0, 1);
        }
        private Mat mul(double... b) {
            double[] result = new double[16];
            for (int row = 0; row < 4; row++)
                for (int col = 0; col < 4; col++)
                    for (int k = 0; k < 4; k++) result[row * 4 + col] += m[row * 4 + k] * b[k * 4 + col];
            m = result;
            return this;
        }
        double[] point(double x, double y, double z) {
            return new double[]{m[0] * x + m[1] * y + m[2] * z + m[3], m[4] * x + m[5] * y + m[6] * z + m[7], m[8] * x + m[9] * y + m[10] * z + m[11]};
        }
        double det() {
            return m[0] * (m[5] * m[10] - m[6] * m[9]) - m[1] * (m[4] * m[10] - m[6] * m[8]) + m[2] * (m[4] * m[9] - m[5] * m[8]);
        }
        /** The same pose for the other hand: conjugated by the x mirror. */
        double[] mirrored() {
            double[] result = m.clone();
            for (int i = 0; i < 16; i++) if ((i / 4 == 0) != (i % 4 == 0)) result[i] = -result[i];
            return result;
        }
    }

    private static Mat of(Consumer<Mat> recipe) {
        Mat mat = new Mat();
        recipe.accept(mat);
        return mat;
    }

    private static Mat hand(int side, float equip, float swing, Use use, int remaining, float partial, int maxUse) {
        return of(m -> OldAnimations.hand(m, side, equip, swing, use, remaining, partial, maxUse));
    }

    private static Mat idle(float equip, float swing) { return hand(1, equip, swing, Use.NONE, 0, 0, 0); }

    private static void assertPose(double[] expected, double[] actual, String what) {
        for (int i = 0; i < 16; i++) assertEquals(expected[i], actual[i], EPS, what + " [" + i / 4 + "][" + i % 4 + "]");
    }

    private static double distance(double[] a, double[] b) {
        double max = 0;
        for (int i = 0; i < 16; i++) max = Math.max(max, Math.abs(a[i] - b[i]));
        return max;
    }

    /** 1.7.10 swing at 0.5: sin(0.25π), sin(√0.5 π), sin(2√0.5 π) = 0.7071068, 0.7956932, -0.9639025; 0 and 1 are at rest. */
    private static Mat swingTurn(Mat m, float swing) {
        return swing == 0.5f ? m.ry(-20 * 0.7071068).rz(-20 * 0.7956932).rx(-80 * 0.7956932) : m;
    }

    @Test void idleHandIs1710AtKeySwingAndEquipPoints() {
        for (float equip : new float[]{0, 1})
            for (float swing : SWINGS) {
                Mat expected = new Mat();
                if (swing == 0.5f) expected.t(-0.4 * 0.7956932, 0.2 * -0.9639025, -0.2);
                expected.t(0.56, -0.52 - 0.6 * equip, -0.72).ry(45);
                swingTurn(expected, swing).s(0.4);
                assertPose(expected.m, idle(equip, swing).m, "idle swing " + swing + " equip " + equip);
            }
    }

    @Test void blockPoseIs1710SwordBlockAndBlockhitKeepsTheSwingTurn() {
        for (float swing : SWINGS) {
            Mat expected = swingTurn(new Mat().t(0.56, -0.52, -0.72).ry(45), swing).s(0.4).t(-0.5, 0.2, 0).ry(30).rx(-80).ry(60);
            assertPose(expected.m, hand(1, 0, swing, Use.BLOCK, 0, 0, BOW).m, "block swing " + swing);
        }
        assertTrue(distance(hand(1, 0, 0.5f, Use.BLOCK, 0, 0, BOW).m, hand(1, 0, 0, Use.BLOCK, 0, 0, BOW).m) > 0.1,
            "a blockhit visibly moves the blocking sword");
    }

    @Test void eatingIs1710AtKeyUseTicks() {
        assertPose(idle(0, 0).m, hand(1, 0, 0, Use.EAT_DRINK, FOOD - 1, 0, FOOD).m, "eating starts from the rest pose");
        // 17 ticks left of 32: past 20% so the jiggle |cos(17/4 π)| * 0.1 shows; lift 1 - (17/32)^27 is 0.99999996.
        double lift = 0.99999996;
        for (float equip : new float[]{0, 1}) {
            Mat expected = new Mat().t(0, 0.0707107, 0).t(0.6 * lift, -0.5 * lift, 0).ry(90 * lift).rx(10 * lift).rz(30 * lift)
                .t(0.56, -0.52 - 0.6 * equip, -0.72).ry(45).s(0.4);
            assertPose(expected.m, hand(1, equip, 0, Use.EAT_DRINK, 16, 0, FOOD).m, "eating, 17 ticks left, equip " + equip);
        }
        // 31.5 ticks left: under 20% done, so no jiggle yet; lift 1 - (31.5/32)^27 = 0.3463646.
        Mat early = new Mat().t(0.6 * 0.3463646, -0.5 * 0.3463646, 0).ry(90 * 0.3463646).rx(10 * 0.3463646).rz(30 * 0.3463646)
            .t(0.56, -0.52, -0.72).ry(45).s(0.4);
        assertPose(early.m, hand(1, 0, 0, Use.EAT_DRINK, 31, 0.5f, FOOD).m, "eating, 31.5 ticks left");
        Mat swinging = new Mat().t(0, 0.0707107, 0).t(0.6 * lift, -0.5 * lift, 0).ry(90 * lift).rx(10 * lift).rz(30 * lift)
            .t(0.56, -0.52, -0.72).ry(45);
        assertPose(swingTurn(swinging, 0.5f).s(0.4).m, hand(1, 0, 0.5f, Use.EAT_DRINK, 16, 0, FOOD).m, "swing while eating");
    }

    @Test void bowIs1710AtKeyDrawTicks() {
        // Drawn 10 ticks: pull (0.25 + 1) / 3; drawn 30: full pull with the drawn-bow shake sin(29.9 * 1.3) * 0.01 * 0.9.
        double[][] points = {{10, 0.4166667, 0.00094679}, {30, 1, 0.00828987}};
        for (double[] point : points) {
            double pull = point[1];
            Mat expected = new Mat().t(0.56, -0.52, -0.72).ry(45).s(0.4).rz(-18).ry(-12).rx(-8).t(-0.9, 0.2, 0)
                .t(0, point[2], 0).t(0, 0, pull * 0.1).rz(-335).ry(-50).t(0, 0.5, 0).s(1, 1, 1 + pull * 0.2).t(0, -0.5, 0).ry(50).rz(335);
            int remaining = BOW - (int) point[0] - 1;
            assertPose(expected.m, hand(1, 0, 0, Use.BOW, remaining, 0, BOW).m, "bow drawn " + point[0]);
        }
    }

    @Test void useTicksHandOverWithoutJumps() {
        for (int remaining = 1; remaining < FOOD; remaining++)
            assertPose(hand(1, 0, 0, Use.EAT_DRINK, remaining, 1, FOOD).m, hand(1, 0, 0, Use.EAT_DRINK, remaining - 1, 0, FOOD).m,
                "eating tick " + remaining);
        for (int remaining = BOW - 40; remaining < BOW; remaining++)
            assertPose(hand(1, 0, 0, Use.BOW, remaining, 1, BOW).m, hand(1, 0, 0, Use.BOW, remaining - 1, 0, BOW).m,
                "bow tick " + remaining);
    }

    @Test void swingIsContinuousAndStartsAndEndsAtRestInEveryPose() {
        for (Use use : Use.values()) {
            int remaining = use == Use.BOW ? BOW - 25 : 12, max = use == Use.BOW ? BOW : FOOD;
            double[] rest = hand(1, 0, 0, use, remaining, 0.3f, max).m;
            assertPose(rest, hand(1, 0, 1, use, remaining, 0.3f, max).m, use + ": a finished swing is back at rest");
            for (int i = 0; i < 1000; i++) {
                float swing = i / 1000f;
                double step = distance(hand(1, 0, swing, use, remaining, 0.3f, max).m, hand(1, 0, swing + 1e-6f, use, remaining, 0.3f, max).m);
                assertTrue(step < 1e-2, use + " jumps at swing " + swing + ": " + step);
            }
        }
    }

    @Test void equippingLowersTheHandStraightDown() {
        for (Use use : new Use[]{Use.NONE, Use.BLOCK, Use.BOW})
            for (float swing : SWINGS) {
                Mat raised = hand(1, 0, swing, use, BOW - 30, 0, BOW), lowered = hand(1, 1, swing, use, BOW - 30, 0, BOW);
                Mat expected = new Mat().t(0, -0.6, 0);
                expected.mul(raised.m);
                assertPose(expected.m, lowered.m, use + " equip 1, swing " + swing);
            }
    }

    @Test void iconPlacementIs1710RenderItemOnTheCentredMesh() {
        Mat renderItem = new Mat().t(0, -0.3, 0).s(1.5).ry(50).rz(335).t(-0.9375, -0.0625, 0);
        Mat expected = new Mat().t(0, -0.3, 0).s(1.5).ry(50).rz(335).t(-0.9375, -0.0625, 0).t(0.5, 0.5, -0.03125).ry(180);
        Mat item = of(m -> OldAnimations.item(m, 1, false));
        assertPose(expected.m, item.m, "flat item");
        assertPose(new Mat().ry(180).mul(expected.m).m, of(m -> OldAnimations.item(m, 1, true)).m, "rods turned round");
        // The texture reads unmirrored from +Z on the centred mesh; 1.7.10 put texel (0, 0) at icon corner (1, 1) on its
        // back face (z -1/16) and texel (1, 1) at (0, 0).
        assertArrayEquals(renderItem.point(1, 1, -0.0625), item.point(-0.5, 0.5, 0.03125), EPS);
        assertArrayEquals(renderItem.point(0, 0, -0.0625), item.point(0.5, -0.5, 0.03125), EPS);
    }

    @Test void legacySwingTakesTheSwingOutsideA17UsePose() {
        for (Use use : Use.values()) {
            assertEquals(use == Use.NONE, OldAnimations.legacySwingShown(true, use), use + " with Legacy Swing on");
            assertFalse(OldAnimations.legacySwingShown(false, use), use + " with Legacy Swing off");
        }
    }

    @Test void legacySwingAloneAnimatesPlacingBlocks() {
        for (Use use : Use.values())
            for (boolean block : new boolean[]{true, false}) {
                assertEquals(use == Use.NONE && block, OldAnimations.legacySwingPlaces(true, use, block),
                    use + (block ? " block item" : " other item") + " with Legacy Swing on");
                assertFalse(OldAnimations.legacySwingPlaces(false, use, block), use + " with Legacy Swing off: 1.7 places it");
            }
    }

    @Test void legacySwingHandIsLegacySwingsMotionWhere17HoldsTheItem() {
        for (int side : new int[]{1, -1})
            for (float equip : new float[]{0, 0.5f, 1}) {
                assertPose(hand(side, equip, 0, Use.NONE, 0, 0, 0).m, of(m -> OldAnimations.legacySwingHand(m, side, equip, 0)).m,
                    "at rest Legacy Swing leaves the item where 1.7 holds it, side " + side + " equip " + equip);
                for (float swing : new float[]{0.1f, 0.5f, 0.9f}) {
                    double t = Math.pow(swing, 4), arc = Math.sin(Math.sqrt(t) * Math.PI);
                    // 1.8.9's LegacySwing189 as it was before the motion moved here, mirrored for the left hand.
                    Mat expected = new Mat().t(side * 0.56, -0.52 - 0.6 * equip, -0.72)
                        .t(side * -arc * 0.55, Math.sin(Math.sqrt(t) * Math.PI * 2) * 0.25, -Math.sin(t * Math.PI) * 0.2)
                        .ry(side * (45 - arc * 20)).rz(side * arc * -20).rx(arc * -80).s(0.4);
                    Mat legacy = of(m -> OldAnimations.legacySwingHand(m, side, equip, swing));
                    assertPose(expected.m, legacy.m, "side " + side + " equip " + equip + " swing " + swing);
                    assertTrue(distance(hand(side, equip, swing, Use.NONE, 0, 0, 0).m, legacy.m) > 0.01, "not 1.7's swing, swing " + swing);
                }
            }
    }

    @Test void modernHandPlusBridgeIsThe1710Hand() {
        for (int side : new int[]{1, -1})
            for (float equip : new float[]{0, 0.5f, 1})
                for (float swing : new float[]{0, 0.1f, 0.5f, 0.9f, 1}) {
                    double root = Math.sqrt(swing), quick = Math.sin(swing * swing * Math.PI), arc = Math.sin(root * Math.PI);
                    // Modern vanilla non-using branch: swing offset, applyItemArmTransform, then the attack turn
                    // (applyItemArmAttackTransform / swingArm) that ends 45° back about Y.
                    Mat modern = new Mat().t(side * -0.4 * arc, 0.2 * Math.sin(root * Math.PI * 2), -0.2 * Math.sin(swing * Math.PI))
                        .t(side * 0.56, -0.52 - 0.6 * equip, -0.72)
                        .ry(side * (45 - 20 * quick)).rz(side * -20 * arc).rx(-80 * arc).ry(side * -45);
                    OldAnimations.fromModernHand(modern, side);
                    assertPose(hand(side, equip, swing, Use.NONE, 0, 0, 0).m, modern.m, "side " + side + " equip " + equip + " swing " + swing);
                }
    }

    @Test void thirdPersonIs1710RenderPlayer() {
        Mat icon = new Mat().t(0, -0.3, 0).s(1.5).ry(50).rz(335).t(-0.9375, -0.0625, 0).t(0.5, 0.5, -0.03125).ry(180);
        Mat toHand = new Mat().t(-0.0625, 0.4375, 0.0625);
        assertPose(toHand.m, of(m -> OldAnimations.thirdPersonHand(m, 1)).m, "hand");
        Mat tool = new Mat().t(0, 0.1875, 0).s(0.625, -0.625, 0.625).rx(-100).ry(45).mul(icon.m).s(1, 1, -1);
        assertPose(tool.m, thirdPerson(Held.TOOL, false).m, "tool");
        Mat blocking = new Mat().t(0.05, 0, -0.1).ry(-50).rx(-10).rz(-60).mul(tool.m);
        assertPose(blocking.m, thirdPerson(Held.TOOL, true).m, "sword block");
        assertPose(new Mat().rz(180).t(0, -0.125, 0).mul(tool.m).m, thirdPerson(Held.ROD, false).m, "rod");
        Mat bow = new Mat().t(0, 0.125, 0.3125).ry(-20).s(0.625, -0.625, 0.625).rx(-100).ry(45).mul(icon.m).s(1, 1, -1);
        assertPose(bow.m, thirdPerson(Held.BOW, false).m, "bow");
        Mat item = new Mat().t(0.25, 0.1875, -0.1875).s(0.375).rz(60).rx(-90).rz(20).mul(icon.m);
        assertPose(item.m, thirdPerson(Held.ITEM, false).m, "other items");
        assertPose(item.m, thirdPerson(Held.ITEM, true).m, "only swords block");
    }

    private static Mat thirdPerson(Held held, boolean blocking) {
        return of(m -> OldAnimations.thirdPersonItem(m, 1, held, blocking));
    }

    @Test void thirdPersonFlipDrawsThe1710SurfaceWithAProperTransform() {
        Mat flipped = new Mat().t(0, 0.1875, 0).s(0.625, -0.625, 0.625).rx(-100).ry(45)
            .t(0, -0.3, 0).s(1.5).ry(50).rz(335).t(-0.9375, -0.0625, 0).t(0.5, 0.5, -0.03125).ry(180);
        Mat ours = thirdPerson(Held.TOOL, false);
        assertTrue(flipped.det() < 0, "1.7.10's tool transform is a mirror");
        assertTrue(ours.det() > 0);
        // The flat mesh is symmetric through its mid-plane: each point lands where 1.7.10 put its mirror twin.
        for (double[] p : new double[][]{{-0.5, 0.5, 0.03125}, {0.5, -0.5, -0.03125}, {0.2, 0.1, 0.03125}})
            assertArrayEquals(flipped.point(p[0], p[1], -p[2]), ours.point(p[0], p[1], p[2]), EPS);
    }

    @Test void leftHandMirrorsTheRightHand() {
        for (Use use : Use.values())
            for (float swing : SWINGS)
                assertPose(hand(1, 0.3f, swing, use, BOW - 25, 0.4f, BOW).mirrored(), hand(-1, 0.3f, swing, use, BOW - 25, 0.4f, BOW).m,
                    "left " + use + " swing " + swing);
        assertPose(of(m -> OldAnimations.item(m, 1, true)).mirrored(), of(m -> OldAnimations.item(m, -1, true)).m, "left rod");
        assertPose(of(m -> OldAnimations.fromModernHand(m, 1)).mirrored(), of(m -> OldAnimations.fromModernHand(m, -1)).m, "left bridge");
        for (Held held : Held.values())
            assertPose(of(m -> { OldAnimations.thirdPersonHand(m, 1); OldAnimations.thirdPersonItem(m, 1, held, true); }).mirrored(),
                of(m -> { OldAnimations.thirdPersonHand(m, -1); OldAnimations.thirdPersonItem(m, -1, held, true); }).m, "left arm " + held);
    }

    @Test void everyRecipeIsAProperTransform() {
        for (int side : new int[]{1, -1}) {
            for (Use use : Use.values())
                for (float swing : SWINGS) {
                    Mat full = hand(side, 0.5f, swing, use, BOW - 25, 0.5f, BOW);
                    OldAnimations.item(full, side, use == Use.NONE);
                    assertTrue(full.det() > 0, use + " swing " + swing + " side " + side);
                }
            for (Held held : Held.values())
                for (boolean blocking : new boolean[]{false, true})
                    assertTrue(of(m -> OldAnimations.thirdPersonItem(m, side, held, blocking)).det() > 0, held + " blocking " + blocking);
        }
        assertTrue(of(m -> OldAnimations.droppedItem(m, 5, 1, 30)).det() > 0);
    }

    @Test void droppedItemsAreHalfSizeIconsFacingTheCamera() {
        for (float yaw : new float[]{0, 90, -135, 180, 271}) {
            Mat m = of(s -> OldAnimations.droppedItem(s, 0, 0, yaw));
            double[] origin = m.point(0, 0, 0), front = m.point(0, 0, 1);
            // Camera at yaw θ looks along (-sin θ, 0, cos θ); the icon's readable +Z face points back at it.
            double r = Math.toRadians(yaw);
            assertArrayEquals(new double[]{0.5 * Math.sin(r), 0, -0.5 * Math.cos(r)},
                new double[]{front[0] - origin[0], front[1] - origin[1], front[2] - origin[2]}, EPS, "yaw " + yaw);
            // Hover sin(0) * 0.1 + 0.1, then the 1.7.10 quad centre 0.25 up at half size.
            assertArrayEquals(new double[]{0, 0.1 + 0.125, 0}, origin, EPS);
        }
        // Hover top: sin(age / 10 + offset) = 1.
        assertEquals(0.2 + 0.125, of(s -> OldAnimations.droppedItem(s, (float) (Math.PI * 5), 0, 0)).point(0, 0, 0)[1], EPS);
    }

    @Test void sneakCameraDropsAtOnceAndEasesBackUp() {
        assertEquals(1.54f, OldAnimations.sneakEyeHeight(1.62f, 1.54f), 1e-6, "1.7.10: 0.08 lower on the next tick");
        assertEquals(1.27f, OldAnimations.sneakEyeHeight(1.62f, 1.27f), 1e-6, "modern crouch height, also at once");
        float eye = 1.54f;
        for (int tick = 1; tick <= 6; tick++) {
            float next = OldAnimations.sneakEyeHeight(eye, 1.62f);
            assertEquals(0.08 * Math.pow(0.4, tick), 1.62f - next, 1e-6, "gap after " + tick + " ticks");
            assertTrue(next > eye && next <= 1.62f);
            eye = next;
        }
        assertEquals(1.62f, OldAnimations.sneakEyeHeight(1.62f, 1.62f), 0);
    }
}
