package com.thelads.core.client.cosmetics.anim;

import org.junit.jupiter.api.Test;

import static com.thelads.core.client.cosmetics.anim.Emote.*;
import static org.junit.jupiter.api.Assertions.*;

/** The spring maths behind the Twerking emote's jiggle, and what it does to the dance. */
class JiggleTest {
    /** One kick of 1 at the start of a 4 s loop: 6 Hz, damping 0.12. */
    private final Jiggle spring = new Jiggle(4000, 6, 0.12f, new float[] {0}, new float[] {1});

    @Test
    void swingsOutOvershootsAndSettles() {
        float peak = 0, trough = 0;
        for (float t = 0; t < 4000; t += 0.25f) {
            float x = spring.at(t);
            peak = Math.max(peak, x);
            trough = Math.min(trough, x);
            assertTrue(Math.abs(x) <= spring.left(t) + 1e-4f, "never outside its decaying envelope: " + x + " at " + t + " ms");
            if (t >= 1500) assertTrue(Math.abs(x) < 0.01f, "settled 1.5 s after the kick: " + x + " at " + t + " ms");
        }
        assertEquals(0, spring.at(0), 1e-4, "a kick is a push, not a jump: it starts from rest");
        assertTrue(peak > 0.75f && peak < 1, "the first swing reaches most of the kick: " + peak);
        assertTrue(trough < -0.5f * peak, "then it overshoots its rest the other way: " + trough);
        assertTrue(trough > -peak, "by less than it swung out: " + trough);
    }

    @Test
    void staysBoundedHoweverItIsKicked() {
        // The worst case: barely damped, and kicked in step with its own ring (every 200 ms at 5 Hz), loop after loop.
        float[] times = {0, 200, 400, 600, 800}, kicks = {1, 1, 1, 1, 1};
        Jiggle resonant = new Jiggle(1000, 5, 0.02f, times, kicks);
        float most = 0;
        for (float t = 0; t < 1000; t += 0.25f) most = Math.max(most, Math.abs(resonant.at(t)));
        assertTrue(most > 3, "kicks in step do add up: " + most);
        assertTrue(Float.isFinite(resonant.bound()) && most <= resonant.bound(), most + " within the bound " + resonant.bound());
        // Kicks against each other, harder damping: still inside the bound, which shrinks with the kicks.
        Jiggle mixed = new Jiggle(1000, 7.3f, 0.3f, times, new float[] {2, -3, .5f, -1, 4});
        for (float t = 0; t < 1000; t += 0.25f) assertTrue(Math.abs(mixed.at(t)) <= mixed.bound(), "bounded at " + t + " ms");
        assertTrue(mixed.bound() < 11, "the sum of the kicks, plus a little from the loops before: " + mixed.bound());
        assertThrows(IllegalArgumentException.class, () -> new Jiggle(1000, 5, 0, times, kicks), "no damping would ring forever");
        assertThrows(IllegalArgumentException.class, () -> new Jiggle(1000, 5, 1, times, kicks), "damping 1 does not overshoot: not a jiggle");
    }

    /** The closed form is a real spring: x'' = -2 d w x' - w^2 x, stepped through 40 loops of kicks, ends up on the same curve. */
    @Test
    void matchesASpringSteppedThroughTheKicks() {
        float period = 900, hz = 5.5f, damping = 0.1f;
        float[] times = {0, 150, 520}, kicks = {1, -0.6f, 1.4f};
        Jiggle closed = new Jiggle(period, hz, damping, times, kicks);
        double w = 2 * Math.PI * hz / 1000, ring = w * Math.sqrt(1 - damping * damping), dt = 0.005, x = 0, v = 0, worst = 0;
        int steps = (int) Math.round(period / dt);
        for (int loop = 0; loop < 40; loop++) {
            for (int i = 0; i < steps; i++) {
                double t = i * dt;
                for (int k = 0; k < times.length; k++) if (Math.abs(t - times[k]) < dt / 2) v += kicks[k] * ring; // the impulse
                if (loop == 39) worst = Math.max(worst, Math.abs(x - closed.at((float) t)));
                v += (-2 * damping * w * v - w * w * x) * dt;
                x += v * dt;
            }
        }
        assertTrue(worst < 0.01, "the stepped spring and the closed form differ by at most " + worst);
        assertEquals(closed.at(0), closed.at(period - 0.001f), 1e-3, "and the loop closes");
    }

    @Test
    void twerkKeepsItsFeetPlantedAndItsJiggleSettlesInTheHold() {
        Emote twerk = EmoteLibrary.get(EmoteLibrary.TWERK);
        assertEquals("Twerking", twerk.name);
        float beat = twerk.periodMs / 8;
        float[] v = new float[CHANNELS];
        double[][] lo = new double[2][3], hi = new double[2][3];
        for (int i = 0; i * 2f < twerk.periodMs; i++) {
            twerk.sample(i * 2f, v);
            for (int c = 0; c < CHANNELS; c++) {
                Jiggle jiggle = twerk.jiggle(c);
                assertTrue(Float.isFinite(v[c]), "channel " + c + " at " + i * 2 + " ms");
                if (jiggle == null) continue;
                float x = jiggle.at(i * 2f);
                // Legs and torso wobble by up to half a radian (29 degrees), pivots by up to 5 px: funny, not broken.
                assertTrue(Math.abs(x) <= jiggle.bound() && Math.abs(x) < (c % 6 < 3 ? 0.5f : 5), "channel " + c + "'s spring stays bounded: " + x);
            }
            assertEquals(0, (sole(v, RIGHT_LEG, 0, 0, 0) + sole(v, LEFT_LEG, 0, 0, 0)) / 2, 1e-3, "the feet's midpoint stays put (x)");
            assertEquals(0, (sole(v, RIGHT_LEG, 2, 0, 0) + sole(v, LEFT_LEG, 2, 0, 0)) / 2, 1e-3, "the feet's midpoint stays put (z)");
            for (int leg = 0; leg < 2; leg++) for (int axis = 0; axis < 3; axis++) {
                double at = sole(v, RIGHT_LEG + leg, axis, 0, 0);
                // Up and down: the sole's lowest corner (a leaning leg stands on an edge).
                if (axis == 1) for (int corner = 0; corner < 4; corner++) at = Math.max(at, sole(v, RIGHT_LEG + leg, 1, (corner & 1) * 4 - 2, (corner & 2) * 2 - 2));
                if (i == 0 || at < lo[leg][axis]) lo[leg][axis] = at;
                if (i == 0 || at > hi[leg][axis]) hi[leg][axis] = at;
            }
        }
        for (int leg = 0; leg < 2; leg++) {
            assertTrue(hi[leg][0] - lo[leg][0] < 1.6 && hi[leg][2] - lo[leg][2] < 1.6, "foot " + leg + " slides about a pixel at most: x "
                + (hi[leg][0] - lo[leg][0]) + ", z " + (hi[leg][2] - lo[leg][2]));
            assertEquals(24, hi[leg][1], 1e-3, "it stands on the ground");
            assertTrue(24 - lo[leg][1] < 1, "and never lifts a pixel off it: " + (24 - lo[leg][1]));
        }
        // The hold (beats 5 to 7.25): the keyframes stand still, so what moves is the springs: a lot at first, hardly at the end.
        double early = swing(twerk, 5 * beat, 5.5f * beat), late = swing(twerk, 6.75f * beat, 7.25f * beat);
        assertTrue(early > 2.5, "the slam throws the right cheek over 2.5 px: " + early);
        assertTrue(late < 0.2 * early, "and it has settled by the end of the hold: " + late + " of " + early);
    }

    /** How far the top of the right leg (its pivot, against the planted feet) travels up and down between two times, in pixels. */
    private static double swing(Emote emote, float fromMs, float toMs) {
        float[] v = new float[CHANNELS];
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (float t = fromMs; t <= toMs; t += 1) {
            emote.sample(t, v);
            double y = v[ROOT + 1] + rest(RIGHT_LEG, 1) + v[ch(RIGHT_LEG, Y)];
            lo = Math.min(lo, y);
            hi = Math.max(hi, y);
        }
        return hi - lo;
    }

    /** A point of a leg's sole, (fx, fz) from its middle, in model space (pixels, root included), along axis 0..2. */
    private static double sole(float[] v, int leg, int axis, int fx, int fz) {
        double rx = v[ch(leg, RX)], ry = v[ch(leg, RY)], rz = v[ch(leg, RZ)];
        double y1 = 12 * Math.cos(rx) - fz * Math.sin(rx), z1 = 12 * Math.sin(rx) + fz * Math.cos(rx);
        double x2 = fx * Math.cos(ry) + z1 * Math.sin(ry), z2 = -fx * Math.sin(ry) + z1 * Math.cos(ry);
        double[] p = {x2 * Math.cos(rz) - y1 * Math.sin(rz), x2 * Math.sin(rz) + y1 * Math.cos(rz), z2};
        return v[ROOT + axis] + rest(leg, axis) + v[ch(leg, X + axis)] + p[axis];
    }
}
