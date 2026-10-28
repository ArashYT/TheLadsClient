package com.thelads.core.client;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class CapeImageGuardTest {

    @Test
    public void testIsHttps() {
        assertTrue(CapeImageGuard.isHttps("https://example.com/cape.png"));
        assertTrue(CapeImageGuard.isHttps("HTTPS://EXAMPLE.COM/CAPE.PNG"));
        assertFalse(CapeImageGuard.isHttps("http://example.com/cape.png"));
        assertFalse(CapeImageGuard.isHttps(null));
        assertFalse(CapeImageGuard.isHttps("ftp://example.com/cape.png"));
    }

    @Test
    public void testFrameCount() {
        assertEquals(1, CapeImageGuard.frameCount(64, 32));
        assertEquals(2, CapeImageGuard.frameCount(64, 64));
        assertEquals(0, CapeImageGuard.frameCount(64, 16));
        assertEquals(0, CapeImageGuard.frameCount(0, 32));
    }

    @Test
    public void testAcceptablePngHeader() {
        byte[] validPngHeader = new byte[]{
                (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A,
                0, 0, 0, 13, // length
                'I', 'H', 'D', 'R',
                0, 0, 0, 64,  // width = 64
                0, 0, 0, 32,  // height = 32
                8, 6, 0, 0, 0 // bit depth, color type, etc.
        };
        assertTrue(CapeImageGuard.acceptablePng(validPngHeader));

        // Too short
        assertFalse(CapeImageGuard.acceptablePng(new byte[]{1, 2, 3}));

        // Invalid signature
        byte[] invalidSig = validPngHeader.clone();
        invalidSig[0] = 0;
        assertFalse(CapeImageGuard.acceptablePng(invalidSig));

        // Excess width > MAX_WIDTH
        byte[] hugeWidth = validPngHeader.clone();
        hugeWidth[18] = 0x10; // width = 4096
        assertFalse(CapeImageGuard.acceptablePng(hugeWidth));
    }

    @Test
    public void testReadJsonLimits() throws IOException {
        String smallJson = "{\"test\":true}";
        String read = CapeImageGuard.readJson(new ByteArrayInputStream(smallJson.getBytes(StandardCharsets.UTF_8)));
        assertEquals(smallJson, read);

        byte[] huge = new byte[CapeImageGuard.MAX_JSON_BYTES + 10];
        assertNull(CapeImageGuard.readJson(new ByteArrayInputStream(huge)));
    }
}
