package com.thelads.core.client.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.jna.Memory;
import org.junit.jupiter.api.Test;

class FileDialogsTest {
    @Test
    void guidIsLaidOutAsWindowsReadsIt() {
        // CLSID_FileOpenDialog as it sits in shell32 and SDL3.dll: Data1-3 little-endian, Data4 as written.
        Memory guid = FileDialogs.Explorer.guid("DC1C5A9C-E88A-4DDE-A5A1-60F82A20AEF7");
        byte[] expected = {(byte) 0x9C, 0x5A, 0x1C, (byte) 0xDC, (byte) 0x8A, (byte) 0xE8, (byte) 0xDE, 0x4D,
            (byte) 0xA5, (byte) 0xA1, 0x60, (byte) 0xF8, 0x2A, 0x20, (byte) 0xAE, (byte) 0xF7};
        assertArrayEquals(expected, guid.getByteArray(0, 16));
    }

    @Test
    void wideStringsRoundTrip() {
        assertEquals("Sounds (MP3, WAV)", FileDialogs.Explorer.read(FileDialogs.Explorer.wide("Sounds (MP3, WAV)")));
    }

    @Test
    void filterMatchesExtensionsAnyCase() {
        assertTrue(FileDialogs.matches("Kill.MP3", "mp3", "wav"));
        assertFalse(FileDialogs.matches("kill.mp3.txt", "mp3", "wav"));
        assertTrue(FileDialogs.matches("anything"));
    }
}
