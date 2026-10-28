package com.thelads.core.client.util;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

class CapePngTest {

    /** A PNG signature and IHDR with this size (the rest of the file never matters to the check). */
    private static byte[] png(int width, int height, int length) {
        ByteBuffer b = ByteBuffer.allocate(length);
        b.putLong(0x89504E470D0A1A0AL).putInt(13).putInt(0x49484452).putInt(width).putInt(height);
        return b.array();
    }

    @Test
    void capeSizesPassBombsDoNot() {
        assertTrue(CapePng.fits(64, 32, false));
        assertTrue(CapePng.fits(46, 22, false), "OptiFine's old cape size");
        assertTrue(CapePng.fits(2048, 1024, false));
        assertFalse(CapePng.fits(8193, 1, false), "the 512 MiB canvas from a 100-byte PNG");
        assertFalse(CapePng.fits(1, 4096, false));
        assertFalse(CapePng.fits(64, 2048, false), "a tall static cape grows its canvas past 2048 wide");

        assertTrue(CapePng.fits(64, 32 * 10, true));
        assertFalse(CapePng.fits(64, 16, true), "no whole frame: the frame counter divided by zero");
        assertFalse(CapePng.fits(64, 32 * 257, true), "over 256 frames");
        assertFalse(CapePng.fits(2048, 4096, true), "over 16 MiB decoded");
    }

    @Test
    void readChecksTheHeaderBeforeAnythingIsDecoded() throws Exception {
        byte[] ok = png(64, 32, 64);
        assertArrayEquals(ok, CapePng.read(new ByteArrayInputStream(ok), false));
        assertNull(CapePng.read(new ByteArrayInputStream(png(8193, 1, 64)), false));
        assertNull(CapePng.read(new ByteArrayInputStream(png(64, 32, CapePng.MAX_BYTES + 1)), false), "over 1 MiB");
        assertNull(CapePng.read(new ByteArrayInputStream(new byte[64]), false), "not a PNG");
        byte[] noIhdr = png(64, 32, 64);
        noIhdr[12] = 'X';
        assertNull(CapePng.read(new ByteArrayInputStream(noIhdr), false), "IHDR must come first");
    }
}
