package com.thelads.core.shared;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class ScreenshotConversionTest {

    @Test
    public void testConvertPixelsBasicVerticalFlipAndColorSwap() {
        // 2x2 image, texture width = 2
        // OpenGL bottom row (y=0 in GL, so row 1 in output):
        //   pixel(0, 0) = 0x00FF0000 (byte 2=FF, byte 0=00 -> should convert to Red in byte 0, Blue in byte 2 -> 0xFFFF0000 with 0xFF alpha)
        //   wait! raw: 0x00RRGGBB.
        //   low byte = 0x11, mid byte = 0x22, high byte = 0x33.
        //   formula: 0xFF000000 | (p & 0x0000FF00) | ((p & 0xFF) << 16) | ((p >> 16) & 0xFF)
        //   pixel(0, 0) = 0x00112233 -> converted: 0xFF000000 | 0x2200 | (0x33 << 16) | 0x11 = 0xFF332211
        // OpenGL top row (y=1 in GL, so row 0 in output):
        //   pixel(0, 1) = 0x00445566 -> converted: 0xFF665544
        //   pixel(1, 1) = 0x00778899 -> converted: 0xFF998877
        int[] raw = new int[] {
            0x00112233, 0x00AABBCC, // GL row 0 (bottom row)
            0x00445566, 0x00778899  // GL row 1 (top row)
        };
        int[] out = new int[4];

        ScreenshotConversion.convertPixels(raw, out, 2, 2, 2);

        // Output row 0 (top row) comes from GL row 1
        assertEquals(0xFF665544, out[0]);
        assertEquals(0xFF998877, out[1]);

        // Output row 1 (bottom row) comes from GL row 0
        assertEquals(0xFF332211, out[2]);
        assertEquals(0xFFCCBBAA, out[3]);
    }

    @Test
    public void testConvertPixelsWithTextureStride() {
        // Visible: 2x2, Texture stride: 4
        // GL row 0: [p0, p1, pad, pad]
        // GL row 1: [p2, p3, pad, pad]
        int[] raw = new int[] {
            0x00010203, 0x00040506, 0xDEAD, 0xBEEF, // GL row 0
            0x00070809, 0x000A0B0C, 0xCAFE, 0xBABE  // GL row 1
        };
        int[] out = new int[4];

        ScreenshotConversion.convertPixels(raw, out, 2, 2, 4);

        // Output row 0 from GL row 1
        assertEquals(0xFF090807, out[0]);
        assertEquals(0xFF0C0B0A, out[1]);

        // Output row 1 from GL row 0
        assertEquals(0xFF030201, out[2]);
        assertEquals(0xFF060504, out[3]);
    }

    @Test
    public void testEdgeCasesDoNotThrow() {
        int[] buf = new int[4];
        ScreenshotConversion.convertPixels(null, buf, 2, 2, 2);
        ScreenshotConversion.convertPixels(buf, null, 2, 2, 2);
        ScreenshotConversion.convertPixels(buf, buf, 0, 2, 2);
        ScreenshotConversion.convertPixels(buf, buf, 2, 0, 2);
        ScreenshotConversion.convertPixels(buf, buf, 2, 2, 0);
        // All default zeros untouched
        assertArrayEquals(new int[4], buf);
    }
}
