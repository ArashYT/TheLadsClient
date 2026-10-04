package com.thelads.core.client;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AppleSkinSyncTest {
    @Test void floatsAreBigEndianIeee754() {
        assertEquals(5f, AppleSkinSync.decodeFloat(new byte[] {0x40, (byte) 0xA0, 0, 0}, AppleSkinSync.MAX_SATURATION));
        assertEquals(0f, AppleSkinSync.decodeFloat(new byte[] {0, 0, 0, 0}, AppleSkinSync.MAX_EXHAUSTION));
        assertEquals(2.25f, AppleSkinSync.decodeFloat(AppleSkinSync.encodeFloat(2.25f), AppleSkinSync.MAX_EXHAUSTION));
        assertArrayEquals(new byte[] {0x40, 0x60, 0, 0}, AppleSkinSync.encodeFloat(3.5f));
    }

    @Test void valuesThatCannotBeThePlayersAreDropped() {
        assertTrue(Float.isNaN(AppleSkinSync.decodeFloat(new byte[] {0x40, 0x60}, AppleSkinSync.MAX_SATURATION)), "too short");
        assertTrue(Float.isNaN(AppleSkinSync.decodeFloat(new byte[0], AppleSkinSync.MAX_SATURATION)));
        assertTrue(Float.isNaN(AppleSkinSync.decodeFloat(null, AppleSkinSync.MAX_SATURATION)));
        assertTrue(Float.isNaN(AppleSkinSync.decodeFloat(AppleSkinSync.encodeFloat(-1f), AppleSkinSync.MAX_SATURATION)), "negative");
        assertTrue(Float.isNaN(AppleSkinSync.decodeFloat(AppleSkinSync.encodeFloat(20.5f), AppleSkinSync.MAX_SATURATION)), "above 20");
        assertEquals(40f, AppleSkinSync.decodeFloat(AppleSkinSync.encodeFloat(40f), AppleSkinSync.MAX_EXHAUSTION));
        assertTrue(Float.isNaN(AppleSkinSync.decodeFloat(AppleSkinSync.encodeFloat(Float.NaN), AppleSkinSync.MAX_EXHAUSTION)));
        assertTrue(Float.isNaN(AppleSkinSync.decodeFloat(AppleSkinSync.encodeFloat(Float.POSITIVE_INFINITY), AppleSkinSync.MAX_EXHAUSTION)));
    }

    @Test void extraBytesAreIgnored() {
        assertEquals(5f, AppleSkinSync.decodeFloat(new byte[] {0x40, (byte) 0xA0, 0, 0, 7}, AppleSkinSync.MAX_SATURATION));
    }

    @Test void naturalRegenerationIsOneBooleanByte() {
        assertEquals(Boolean.TRUE, AppleSkinSync.decodeBoolean(new byte[] {1}));
        assertEquals(Boolean.FALSE, AppleSkinSync.decodeBoolean(new byte[] {0}));
        assertNull(AppleSkinSync.decodeBoolean(new byte[0]));
    }
}
