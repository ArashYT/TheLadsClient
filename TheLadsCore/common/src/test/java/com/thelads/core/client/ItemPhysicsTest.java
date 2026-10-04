package com.thelads.core.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ItemPhysicsTest {
    /** Lowest and highest y of the model box (centre c, half sizes h) under copy {@code copy}'s placement. */
    private static double[] span(float pitch, float[] c, float[] h, int copy, int copies) {
        OldAnimationsTest.Mat m = new OldAnimationsTest.Mat();
        ItemPhysics.place(m, 33, pitch, 0, c[0], c[1], c[2], h[1], h[2], copy, copies, 7);
        double low = Double.MAX_VALUE, high = -Double.MAX_VALUE;
        for (int corner = 0; corner < 8; corner++) {
            double y = m.point(c[0] + ((corner & 1) == 0 ? -h[0] : h[0]), c[1] + ((corner & 2) == 0 ? -h[1] : h[1]),
                c[2] + ((corner & 4) == 0 ? -h[2] : h[2]))[1];
            low = Math.min(low, y);
            high = Math.max(high, y);
        }
        return new double[]{low, high};
    }

    @Test
    void itemsRestOnTheGroundAtEveryTumble() {
        float[] flatCentre = {0, 0.125f, 0}, flat = {0.25f, 0.25f, 1 / 64f};        // 26.x generated item, ground transform
        float[] blockCentre = {0, 0.1875f, 0}, block = {0.125f, 0.125f, 0.125f};
        double[] lying = span(0, flatCentre, flat, 0, 1);
        assertEquals(ItemPhysics.LIFT, lying[0], 1e-5, "a resting flat item sits just above the ground");
        assertEquals(2 / 64.0, lying[1] - lying[0], 1e-5, "and lies on its face");
        assertEquals(ItemPhysics.LIFT, span(180, flatCentre, flat, 0, 1)[0], 1e-5, "on either face");
        assertEquals(0.25, span(0, blockCentre, block, 0, 1)[1] - ItemPhysics.LIFT, 1e-5, "a block rests on a side");
        for (float pitch = 0; pitch < 360; pitch += 7.5f) {
            assertTrue(span(pitch, flatCentre, flat, 0, 1)[0] >= ItemPhysics.LIFT - 1e-5, "a tumbling item never dips into the ground");
            assertTrue(span(pitch, blockCentre, block, 0, 1)[0] >= ItemPhysics.LIFT - 1e-5, "nor a tumbling block");
            for (int copy = 0; copy < 5; copy++) {
                assertTrue(span(pitch, flatCentre, flat, copy, 5)[0] >= ItemPhysics.LIFT - 1e-5, "nor a copy in a stack of five sheets");
                assertTrue(span(pitch, blockCentre, block, copy, 5)[0] >= ItemPhysics.LIFT - 1e-5, "nor a scattered block copy");
            }
        }
    }

    @Test
    void tumblesInTheAirAndSettlesWithoutSnapping() {
        ItemPhysics.Tumble tumble = new ItemPhysics.Tumble(40);
        assertEquals(40, tumble.yaw(0.5f), 0, "starts at its own yaw");
        for (int tick = 0; tick < 3; tick++) tumble.tick(0.5, false, false);
        assertEquals(60, tumble.pitch(1), 1e-4, "spins 20 degrees a tick at half a block a tick");
        assertEquals(50, tumble.pitch(0.5f), 1e-4, "interpolated between ticks");
        float before = tumble.pitch(1);
        tumble.tick(0, true, false);
        float step = tumble.pitch(1) - before;
        assertTrue(step < 0 && step > -20, "lands easing back towards lying flat: " + step);
        for (int tick = 0; tick < 40; tick++) {
            float last = tumble.pitch(1);
            tumble.tick(0, true, false);
            assertTrue(Math.abs(tumble.pitch(1) - last) <= Math.abs(step) + 1e-4, "each step no larger than the first");
        }
        assertEquals(0, tumble.pitch(1), 0, "and comes to rest exactly");
        assertEquals(40, tumble.yaw(1), 0, "keeping its yaw");
        tumble.rest = 90;
        for (int tick = 0; tick < 4; tick++) tumble.tick(1, false, false);
        for (int tick = 0; tick < 60; tick++) tumble.tick(0, true, false);
        assertEquals(0, tumble.pitch(1) % 90, 0, "a block comes to rest on a side");
        assertEquals(0, tumble.raise(1), "a resting item is drawn at its entity");
        for (int tick = 0; tick < 30; tick++) {
            float last = tumble.raise(1);
            tumble.tick(0, false, true);
            assertTrue(tumble.raise(1) >= last && tumble.raise(1) - last <= ItemPhysics.FLOAT * 0.3f + 1e-6, "eases up onto the surface");
        }
        assertEquals(ItemPhysics.FLOAT, tumble.raise(1), 1e-4, "and floats on it");
        assertTrue(tumble.yaw(1) > 40, "afloat it turns slowly");
    }

    @Test
    void materialRules() {
        assertTrue(ItemPhysics.burns("oak_planks", true, true) && ItemPhysics.floats("oak_planks", true, true), "wood burns and floats");
        assertTrue(ItemPhysics.burns("white_wool", false, false) && ItemPhysics.burns("leather_boots", false, false), "wool and leather burn");
        assertFalse(ItemPhysics.burns("stone", false, false) || ItemPhysics.floats("iron_ingot", false, false), "stone and iron do not");
        assertFalse(ItemPhysics.floats("bedrock", false, false) || ItemPhysics.burns("bookshelf_stone", false, false), "whole words only");
        assertTrue(ItemPhysics.floats("packed_ice", false, false) && !ItemPhysics.burns("packed_ice", false, false), "ice floats unburnt");
        assertEquals(6000, ItemPhysics.despawnTicks(5));
    }

    @Test
    void waterSpeeds() {
        double vy = -0.3;
        for (int tick = 0; tick < 200; tick++) vy = ItemPhysics.buoyancy(vy, true);
        assertEquals(0.06, vy, 1e-9, "a floating item rises steadily");
        vy = 0.2;
        for (int tick = 0; tick < 200; tick++) vy = ItemPhysics.buoyancy(vy, false);
        assertEquals(-0.06, vy, 1e-6, "a heavy one sinks slowly");
    }

    @Test
    void chargedThrow() {
        ItemPhysics.Charge charge = new ItemPhysics.Charge();
        assertEquals(0, charge.tick(false, false), "idle");
        assertEquals(1, charge.tick(false, true), "a press and release within a tick is vanilla's throw");
        assertEquals(0, charge.tick(true, true));
        assertEquals(0, charge.shown(), "a tap shows no bar");
        assertEquals(1, charge.tick(false, false), "a one-tick tap too");
        for (int tick = 0; tick < 11; tick++) assertEquals(0, charge.tick(true, false), "charging throws nothing");
        assertEquals(0.5f, charge.shown(), 1e-6);
        assertEquals(1.75f, charge.tick(false, false), 1e-6, "half charged");
        for (int tick = 0; tick < 60; tick++) charge.tick(true, false);
        assertEquals(1, charge.shown());
        assertEquals(2.5f, charge.tick(false, false), 1e-6, "full power caps");
        charge.tick(true, false);
        charge.reset();
        assertEquals(0, charge.tick(false, false), "a reset charge (a screen opened) throws nothing");
    }
}
