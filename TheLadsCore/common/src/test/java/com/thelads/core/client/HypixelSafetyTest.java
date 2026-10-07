package com.thelads.core.client;

import com.thelads.core.client.bridge.DefaultGameBridge;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.config.Module;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class HypixelSafetyTest {

    private static class TestGameBridge extends DefaultGameBridge {
        private String server = "Singleplayer";

        public void setServer(String s) { this.server = s; }

        @Override public String getServerAddress() { return server; }
        @Override public boolean isIngame() { return true; }
    }

    @AfterEach
    public void resetBridge() {
        LadsGameBridge.set(new DefaultGameBridge());
    }

    @Test
    public void testHypixelAddressDetection() {
        assertTrue(HypixelSafetyManager.isHypixelAddress("mc.hypixel.net"));
        assertTrue(HypixelSafetyManager.isHypixelAddress("hypixel.net"));
        assertTrue(HypixelSafetyManager.isHypixelAddress("play.hypixel.net:25565"));
        assertTrue(HypixelSafetyManager.isHypixelAddress("alpha.hypixel.net"));
        assertTrue(HypixelSafetyManager.isHypixelAddress("hypixel.io"));
        assertTrue(HypixelSafetyManager.isHypixelAddress("test.hypixel.io:25565"));

        assertFalse(HypixelSafetyManager.isHypixelAddress("Singleplayer"));
        assertFalse(HypixelSafetyManager.isHypixelAddress(null));
        assertFalse(HypixelSafetyManager.isHypixelAddress(""));
        assertFalse(HypixelSafetyManager.isHypixelAddress("localhost"));
        assertFalse(HypixelSafetyManager.isHypixelAddress("hypixel.net.fake.com"));
        assertFalse(HypixelSafetyManager.isHypixelAddress("192.168.1.1"));
        assertFalse(HypixelSafetyManager.isHypixelAddress("play.hivemc.com"));
    }

    @Test
    public void testDisallowedModuleList() {
        assertTrue(HypixelSafetyManager.isDisallowedModule("Minimap"));
        assertTrue(HypixelSafetyManager.isDisallowedModule("XaeroMinimap"));
        assertTrue(HypixelSafetyManager.isDisallowedModule("XaeroWorldMap"));
        assertTrue(HypixelSafetyManager.isDisallowedModule("AutoReconnect"));
        assertTrue(HypixelSafetyManager.isDisallowedModule("MouseTweaks"));
        assertTrue(HypixelSafetyManager.isDisallowedModule("ReachDisplay"));
        assertTrue(HypixelSafetyManager.isDisallowedModule("Jade"));
        assertTrue(HypixelSafetyManager.isDisallowedModule("ShulkerBoxUtils"));

        // Allowed modules
        assertFalse(HypixelSafetyManager.isDisallowedModule("FPS"));
        assertFalse(HypixelSafetyManager.isDisallowedModule("CPS"));
        assertFalse(HypixelSafetyManager.isDisallowedModule("Coordinates"));
        assertFalse(HypixelSafetyManager.isDisallowedModule("Keystrokes"));
        assertFalse(HypixelSafetyManager.isDisallowedModule("ArmorHUD"));
        assertFalse(HypixelSafetyManager.isDisallowedModule("Fullbright"));
    }

    @Test
    public void testAutoDisableOnHypixel() {
        TestGameBridge bridge = new TestGameBridge();
        LadsGameBridge.set(bridge);

        Module minimap = new Module("Minimap", "Minimap description");
        minimap.setEnabled(true);

        Module fps = new Module("FPS", "FPS counter");
        fps.setEnabled(true);

        // On singleplayer: both are enabled
        bridge.setServer("Singleplayer");
        assertFalse(minimap.isBlockedByServer());
        assertTrue(minimap.isEnabled());
        assertFalse(fps.isBlockedByServer());
        assertTrue(fps.isEnabled());

        // Connect to Hypixel: Minimap is automatically blocked & disabled
        bridge.setServer("mc.hypixel.net");
        assertTrue(minimap.isBlockedByServer());
        assertFalse(minimap.isEnabled());

        // FPS is allowed and remains enabled
        assertFalse(fps.isBlockedByServer());
        assertTrue(fps.isEnabled());

        // Disconnect back to singleplayer: Minimap is unblocked and returns to saved enabled state
        bridge.setServer("Singleplayer");
        assertFalse(minimap.isBlockedByServer());
        assertTrue(minimap.isEnabled());
    }

    @Test
    public void testAutoReconnectSuppressedOnHypixel() {
        ReconnectSession session = new ReconnectSession();
        UUID player = UUID.randomUUID();
        session.begin("mc.hypixel.net", () -> {}, player);

        assertFalse(session.canRetry(player, true));

        ReconnectSettings settings = new ReconnectSettings();
        session.disconnected(new Object(), List.of("disconnect.timeout"), "Timed out", settings, false, false, System.nanoTime());

        // Should not be due or pending
        assertFalse(session.due(new Object(), System.nanoTime() + 10_000_000_000L));
    }
}
