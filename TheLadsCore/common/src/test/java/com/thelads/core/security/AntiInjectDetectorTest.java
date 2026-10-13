package com.thelads.core.security;

import com.thelads.core.client.bridge.DefaultGameBridge;
import com.thelads.core.client.bridge.LadsGameBridge;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class AntiInjectDetectorTest {

    private final AtomicReference<String> disconnectedTitle = new AtomicReference<>();
    private final AtomicReference<String> disconnectedReason = new AtomicReference<>();

    @BeforeEach
    public void setUp() {
        AntiInjectDetector.resetDetectionForTest();
        disconnectedTitle.set(null);
        disconnectedReason.set(null);

        LadsGameBridge.set(new DefaultGameBridge() {
            @Override
            public void disconnectWithReason(String title, String message) {
                disconnectedTitle.set(title);
                disconnectedReason.set(message);
            }
        });
    }

    @AfterEach
    public void tearDown() {
        AntiInjectDetector.resetDetectionForTest();
        File alert = new File(".lads-antiinject-alert");
        if (alert.exists()) {
            alert.delete();
        }
    }

    @Test
    public void testCheatSignatures() {
        assertTrue(AntiInjectDetector.isCheatSignature("vape.client.Loader"));
        assertTrue(AntiInjectDetector.isCheatSignature("vape/client/Loader"));
        assertTrue(AntiInjectDetector.isCheatSignature("vape$Inner"));
        assertTrue(AntiInjectDetector.isCheatSignature("me.chazz.raven.RavenClient"));
        assertTrue(AntiInjectDetector.isCheatSignature("raven.client.Main"));
        assertTrue(AntiInjectDetector.isCheatSignature("keystrokesmod.client.KeyStrokes"));
        assertTrue(AntiInjectDetector.isCheatSignature("rise.client.Main"));
        assertTrue(AntiInjectDetector.isCheatSignature("net.vantage.rise.Module"));
        assertTrue(AntiInjectDetector.isCheatSignature("slinky.loader.Native"));
        assertTrue(AntiInjectDetector.isCheatSignature("breeze.client.Hook"));
        assertTrue(AntiInjectDetector.isCheatSignature("drip.client.Loader"));
        assertTrue(AntiInjectDetector.isCheatSignature("liquidbounce.client.Main"));
        assertTrue(AntiInjectDetector.isCheatSignature("entropy.client.Core"));
        assertTrue(AntiInjectDetector.isCheatSignature("whiteout.client.Hook"));
        assertTrue(AntiInjectDetector.isCheatSignature("ghostclient.helper.Injector"));

        // Legitimate packages must NEVER trigger false positives
        assertFalse(AntiInjectDetector.isCheatSignature("net.minecraft.client.Minecraft"));
        assertFalse(AntiInjectDetector.isCheatSignature("net.minecraftforge.fml.common.Loader"));
        assertFalse(AntiInjectDetector.isCheatSignature("net.fabricmc.loader.api.FabricLoader"));
        assertFalse(AntiInjectDetector.isCheatSignature("com.thelads.core.security.AntiInjectDetector"));
        assertFalse(AntiInjectDetector.isCheatSignature("java.lang.String"));
        assertFalse(AntiInjectDetector.isCheatSignature("org.lwjgl.opengl.GL11"));
        assertFalse(AntiInjectDetector.isCheatSignature(null));
        assertFalse(AntiInjectDetector.isCheatSignature(""));
    }

    @Test
    public void testCleanEnvironmentReturnsNull() {
        assertNull(AntiInjectDetector.scanOnce());
    }

    @Test
    public void testWatchdogAlertTriggersDetection() throws Exception {
        File alert = new File(".lads-antiinject-alert");
        Files.writeString(alert.toPath(), "Foreign thread TID=1234");

        try {
            String finding = AntiInjectDetector.scanOnce();
            assertNotNull(finding);
            assertTrue(finding.contains("Watchdog alert"));
            assertTrue(finding.contains("Foreign thread TID=1234"));
            assertFalse(alert.exists(), "Alert file should be consumed");
        } finally {
            if (alert.exists()) alert.delete();
        }
    }

    @Test
    public void testOnCheatDetectedDisconnects() {
        AntiInjectDetector.onCheatDetected("test finding");
        assertEquals("Security Violation", disconnectedTitle.get());
        assertNotNull(disconnectedReason.get());
        assertTrue(disconnectedReason.get().contains("test finding"));
        assertTrue(disconnectedReason.get().contains("Disconnected from server"));
    }
}
