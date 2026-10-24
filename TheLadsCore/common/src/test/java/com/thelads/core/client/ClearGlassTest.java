package com.thelads.core.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ClearGlassTest {
    private static final int FRAME = 0xFFFFFFFF, STREAK = 0x80FFFFFF, CLEAR = 0x00123456;

    /** 16x16 glass frames: an opaque frame, clear inside with a diagonal streak. */
    private static int[] glass(int frames) {
        int[] px = new int[16 * 16 * frames];
        for (int f = 0; f < frames; f++)
            for (int y = 0; y < 16; y++)
                for (int x = 0; x < 16; x++)
                    px[(f * 16 + y) * 16 + x] = x == 0 || y == 0 || x == 15 || y == 15 ? FRAME : x == y ? STREAK : CLEAR;
        return px;
    }

    @Test
    void streaksGoFrameStays() {
        int[] px = glass(1);
        ClearGlass.clear(px, 16, 16);
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 16; x++)
                assertEquals(x == 0 || y == 0 || x == 15 || y == 15 ? FRAME : 0, px[y * 16 + x], x + "," + y);
    }

    @Test
    void stainedGlassKeepsItsTint() {
        int tint = 0x7F3060C0;
        int[] px = new int[16 * 16];
        Arrays.fill(px, tint);
        px[5 * 16 + 5] = 0xFF000000;
        ClearGlass.clear(px, 16, 16);
        assertEquals(tint, px[5 * 16 + 5]);
    }

    @Test
    void everyAnimationFrame() {
        int[] px = glass(3);
        ClearGlass.clear(px, 16, 16);
        assertEquals(0, px[(2 * 16 + 7) * 16 + 7]);
        assertEquals(FRAME, px[(2 * 16) * 16 + 7]);
    }

    @Test
    void hdTexturesKeepAWiderFrame() {
        int[] px = new int[32 * 32];
        for (int y = 0; y < 32; y++)
            for (int x = 0; x < 32; x++) px[y * 32 + x] = x < 2 || y < 2 || x > 29 || y > 29 ? FRAME : x == y ? STREAK : CLEAR;
        ClearGlass.clear(px, 32, 32);
        assertEquals(FRAME, px[32 + 10]);
        assertEquals(0, px[10 * 32 + 10]);
    }

    @Test
    void tinyTexturesAreLeftAlone() {
        int[] px = {1, 2, 3, 4};
        ClearGlass.clear(px, 2, 2);
        assertArrayEquals(new int[]{1, 2, 3, 4}, px);
    }

    @Test
    void exactNamesOnly() {
        assertTrue(ClearGlass.isGlass("block/glass"));
        assertTrue(ClearGlass.isGlass("block/light_gray_stained_glass"));
        assertFalse(ClearGlass.isGlass("block/glass_pane_top"));
        assertFalse(ClearGlass.isGlass("block/tinted_glass"));
        assertFalse(ClearGlass.isGlass("block/red_stained_glass_pane_top"));
        assertTrue(ClearGlass.isGlass189("minecraft:blocks/glass_silver"));
        assertFalse(ClearGlass.isGlass189("minecraft:blocks/glass_pane_top_silver"));
    }
}
