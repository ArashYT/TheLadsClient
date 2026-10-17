package com.thelads.core.client;

import com.thelads.core.client.jade.JadeMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The Jade overlay's pure helpers (JadeMath): note names, percentages, numbers, horse jumps, anchoring, fading and titles. */
class JadeMathTest {
    @Test
    void noteNamesWrapEveryOctave() {
        assertEquals("F♯", JadeMath.noteName(0));
        assertEquals("C", JadeMath.noteName(6));
        assertEquals("F♯", JadeMath.noteName(24));
        assertEquals("F", JadeMath.noteName(-1));
    }

    @Test
    void percentRoundsAndClamps() {
        assertEquals(43, JadeMath.percent(3, 7));
        assertEquals(100, JadeMath.percent(7, 7));
        assertEquals(100, JadeMath.percent(9, 7));
        assertEquals(0, JadeMath.percent(-1, 7));
        assertEquals(100, JadeMath.percent(0, 0));
    }

    @Test
    void numbersKeepOneDecimalOnlyWhenNeeded() {
        assertEquals("20", JadeMath.number(20.0));
        assertEquals("15.5", JadeMath.number(15.5));
        assertEquals("3.3", JadeMath.number(3.25));
        assertEquals("0", JadeMath.number(0.04));
    }

    @Test
    void horseJumpHeight() {
        assertEquals(2.89, JadeMath.jumpHeight(0.7), 0.01);
        assertEquals(0, JadeMath.jumpHeight(0), 1e-9);
    }

    @Test
    void placeOnTheAnchorGridStaysOnScreen() {
        assertEquals(JadeMath.EDGE, JadeMath.place(0, 320, 100, 0));
        assertEquals(110, JadeMath.place(1, 320, 100, 0));
        assertEquals(320 - 100 - JadeMath.EDGE, JadeMath.place(2, 320, 100, 0));
        assertEquals(220, JadeMath.place(2, 320, 100, 50));
        assertEquals(0, JadeMath.place(0, 320, 100, -50));
        assertEquals(0, JadeMath.place(1, 50, 100, 0));
    }

    @Test
    void approachStopsAtTheGoal() {
        assertEquals(0.25f, JadeMath.approach(0, 1, 0.25f), 1e-6);
        assertEquals(1f, JadeMath.approach(0.9f, 1, 0.25f), 1e-6);
        assertEquals(0.6f, JadeMath.approach(1, 0, 0.4f), 1e-6);
        assertEquals(0f, JadeMath.approach(0.1f, 0, 0.4f), 1e-6);
    }

    @Test
    void titleCasesIds() {
        assertEquals("Iron Xylophone", JadeMath.title("iron_xylophone"));
        assertEquals("My Mod", JadeMath.title("my-mod"));
        assertEquals("Pickaxe", JadeMath.title("pickaxe"));
    }
}
