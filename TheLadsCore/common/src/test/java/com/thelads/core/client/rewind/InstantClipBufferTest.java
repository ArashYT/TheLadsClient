package com.thelads.core.client.rewind;

import static org.junit.jupiter.api.Assertions.*;

import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class InstantClipBufferTest {
    private File tempDir;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("lads-clip-test").toFile();
        Module mod = ModuleManager.getInstance().getModule(InstantClipBuffer.NAME);
        if (mod != null) mod.setEnabled(true);
        InstantClipBuffer.clear();
        InstantClipBuffer.setDurationSeconds(30);
        InstantClipBuffer.setMemoryCapBytes(64 * 1024 * 1024L);
    }

    @AfterEach
    void tearDown() {
        InstantClipBuffer.clear();
        if (tempDir != null && tempDir.exists()) {
            File[] files = tempDir.listFiles();
            if (files != null) for (File f : files) f.delete();
            tempDir.delete();
        }
    }

    @Test
    void testRecordPacketsAndPruningByTime() {
        long now = 100_000L;
        InstantClipBuffer.setDurationSeconds(10); // 10s window

        // Add packet at t=100s
        InstantClipBuffer.recordPacket(now, 1, new byte[]{10, 20});
        assertEquals(1, InstantClipBuffer.getPacketCount());

        // Add packet at t=105s
        InstantClipBuffer.recordPacket(now + 5000L, 2, new byte[]{30, 40});
        assertEquals(2, InstantClipBuffer.getPacketCount());

        // Add packet at t=112s (first packet at 100s is > 10s old, should be pruned)
        InstantClipBuffer.recordPacket(now + 12000L, 3, new byte[]{50, 60});
        assertEquals(2, InstantClipBuffer.getPacketCount()); // packets at 105s and 112s remain
    }

    @Test
    void testMemoryCapEviction() {
        // Set small memory cap of 1 KB
        InstantClipBuffer.setMemoryCapBytes(1024L);

        byte[] chunk = new byte[300]; // each record is 24 + 300 = 324 bytes
        long now = 1_000L;

        InstantClipBuffer.recordPacket(now, 1, chunk);
        InstantClipBuffer.recordPacket(now + 10, 2, chunk);
        InstantClipBuffer.recordPacket(now + 20, 3, chunk);
        assertEquals(3, InstantClipBuffer.getPacketCount());
        assertTrue(InstantClipBuffer.getCurrentMemoryBytes() <= 1024L);

        // Adding 4th packet (324 * 4 = 1296 > 1024) must evict oldest
        InstantClipBuffer.recordPacket(now + 30, 4, chunk);
        assertTrue(InstantClipBuffer.getCurrentMemoryBytes() <= 1024L);
        assertEquals(3, InstantClipBuffer.getPacketCount());
    }

    @Test
    void testSaveClipCreatesValidMcprArchive() throws Exception {
        long now = 1000L;
        InstantClipBuffer.recordPacket(now, 1, new byte[]{1, 2, 3});
        InstantClipBuffer.recordPacket(now + 100, 2, new byte[]{4, 5});
        InstantClipBuffer.recordPacket(now + 500, 3, new byte[]{6, 7, 8, 9});

        File clip = InstantClipBuffer.saveClip(tempDir, "mc.hypixel.net", "1.8.9", 47);
        assertNotNull(clip);
        assertTrue(clip.exists());
        assertTrue(clip.getName().endsWith(".mcpr"));
        assertTrue(clip.length() > 0);

        // Verify ZIP contents (meta.json and recording.tmcpr)
        try (ZipFile zip = new ZipFile(clip)) {
            ZipEntry meta = zip.getEntry("meta.json");
            assertNotNull(meta, "meta.json must exist in .mcpr");
            try (InputStream is = zip.getInputStream(meta)) {
                String metaContent = new String(is.readAllBytes());
                assertTrue(metaContent.contains("\"fileFormat\": \"MCPR\""));
                assertTrue(metaContent.contains("\"protocol\": 47"));
                assertTrue(metaContent.contains("\"serverName\": \"mc.hypixel.net\""));
            }

            ZipEntry tmcpr = zip.getEntry("recording.tmcpr");
            assertNotNull(tmcpr, "recording.tmcpr must exist in .mcpr");
            assertTrue(tmcpr.getSize() > 0);
        }
    }
}
