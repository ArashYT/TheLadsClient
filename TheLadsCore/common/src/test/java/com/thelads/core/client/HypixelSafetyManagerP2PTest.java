package com.thelads.core.client;

import com.thelads.core.client.bridge.DefaultGameBridge;
import com.thelads.core.client.bridge.LadsGameBridge;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class HypixelSafetyManagerP2PTest {

    private static class LoopbackBridge extends DefaultGameBridge {
        private String server = "127.0.0.1:25565";
        private boolean ingame = true;

        public void setServer(String s) { this.server = s; }
        public void setIngame(boolean ig) { this.ingame = ig; }

        @Override public String getServerAddress() { return server; }
        @Override public boolean isIngame() { return ingame; }
    }

    @TempDir
    Path tmp;

    @BeforeEach
    public void setup() {
        HypixelSafetyManager.setBypassFile(tmp.resolve("server_bypasses.json").toFile());
        HypixelSafetyManager.setP2PSession(null, null);
    }

    @AfterEach
    public void cleanup() {
        HypixelSafetyManager.setP2PSession(null, null);
        HypixelSafetyManager.setBypassFile(null);
        LadsGameBridge.set(new DefaultGameBridge());
    }

    @Test
    public void p2pSessionIsUnknownServerEvenOnLoopback() {
        LoopbackBridge bridge = new LoopbackBridge();
        LadsGameBridge.set(bridge);

        // Normally, loopback 127.0.0.1 is trusted:
        assertTrue(HypixelSafetyManager.isTrustedServer());
        assertFalse(HypixelSafetyManager.isDisallowed("Minimap"));
        assertFalse(HypixelSafetyManager.isDisallowed("StackRefill"));

        // When a P2P session is active:
        String p2pKey = "p2p:12345678-1234-1234-1234-123456789abc/world-test";
        HypixelSafetyManager.setP2PSession(p2pKey, "127.0.0.1:25565"); // connected to the guest proxy

        assertEquals(p2pKey, HypixelSafetyManager.getP2PSession());
        assertEquals(p2pKey, HypixelSafetyManager.currentHost());
        assertFalse(HypixelSafetyManager.isTrustedServer(), "P2P session must NOT be trusted even on 127.0.0.1");
        assertTrue(HypixelSafetyManager.isMultiplayer());

        // Risky modules must be blocked as UNKNOWN
        assertEquals(HypixelSafetyManager.Block.UNKNOWN, HypixelSafetyManager.blockOf("Minimap"));
        assertTrue(HypixelSafetyManager.isDisallowed("Minimap"));
        assertEquals(HypixelSafetyManager.Block.UNKNOWN, HypixelSafetyManager.blockOf("StackRefill"));
        assertTrue(HypixelSafetyManager.isDisallowed("StackRefill"));

        // Bypass under the p2p key works
        HypixelSafetyManager.bypass("Minimap");
        assertTrue(HypixelSafetyManager.isBypassed("Minimap"));
        assertFalse(HypixelSafetyManager.isDisallowed("Minimap"));
        assertTrue(HypixelSafetyManager.isDisallowed("StackRefill"));

        // Once P2P session ends, loopback trust is restored
        HypixelSafetyManager.setP2PSession(null, null);
        assertNull(HypixelSafetyManager.getP2PSession());
        assertTrue(HypixelSafetyManager.isTrustedServer());
        assertFalse(HypixelSafetyManager.isDisallowed("Minimap"));
        assertFalse(HypixelSafetyManager.isDisallowed("StackRefill"));
    }

    /** The p2p key applies only while the connection IS the guest proxy: a stale key can't mask or grant anything elsewhere. */
    @Test
    public void staleP2PKeyNeverAppliesToAnotherAddress() {
        LoopbackBridge bridge = new LoopbackBridge();
        LadsGameBridge.set(bridge);
        String p2pKey = "p2p:12345678-1234-1234-1234-123456789abc/world-test";
        HypixelSafetyManager.setP2PSession(p2pKey, "127.0.0.1:50000");

        bridge.setServer("mc.hypixel.net");
        assertTrue(HypixelSafetyManager.isHypixel(), "Hypixel stays Hypixel with a leftover p2p key");
        assertNotEquals(p2pKey, HypixelSafetyManager.currentHost());
        assertTrue(HypixelSafetyManager.isDisallowed("Minimap"));

        bridge.setServer("Singleplayer");
        assertFalse(HypixelSafetyManager.isMultiplayer(), "a leftover key does not turn singleplayer into a server");

        bridge.setServer("127.0.0.1:50000");
        assertEquals(p2pKey, HypixelSafetyManager.currentHost(), "on the proxy itself the session is the p2p key");
        assertFalse(HypixelSafetyManager.isTrustedServer());
    }
}
