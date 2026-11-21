package com.thelads.core.client;

import static org.junit.jupiter.api.Assertions.*;

import com.thelads.core.client.HypixelSafetyManager.Block;
import com.thelads.core.client.bridge.DefaultGameBridge;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.modules.InstantHotbarModule;
import com.thelads.core.modules.OffhandKeyModule;
import com.thelads.core.modules.SmoothSingleplayerModule;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Anti-Ban proof: Offhand Key is off on Hypixel and unknown servers, on in singleplayer and on our own servers; the other two are visual/local only. */
class OffhandKeyGateTest {
    private static final String HYPIXEL = "mc.hypixel.net", UNKNOWN = "play.example.org", OWN = "arashyt.ca", SINGLE = "Singleplayer";
    private static final String NAME = OffhandKeyModule.NAME;

    @TempDir Path tmp;
    private String server = SINGLE;

    @BeforeEach void setUp() {
        HypixelSafetyManager.setBypassFile(tmp.resolve("server_bypasses.json").toFile());
        LadsGameBridge.set(new DefaultGameBridge() {
            @Override public String getServerAddress() { return server; }
            @Override public boolean isIngame() { return true; }
        });
    }

    @AfterEach void tearDown() {
        HypixelSafetyManager.setBypassFile(null);
        LadsGameBridge.set(new DefaultGameBridge());
    }

    @Test void nameIsOffhandKey() {
        assertEquals("Offhand Key", NAME);
    }

    @Test void blockedOnHypixel() {
        server = HYPIXEL;
        assertEquals(Block.BANNED, HypixelSafetyManager.blockOf(NAME));
        assertTrue(HypixelSafetyManager.isDisallowed(NAME));
    }

    @Test void blockedOnUnknownMultiplayerServer() {
        server = UNKNOWN;
        assertNotEquals(Block.NONE, HypixelSafetyManager.blockOf(NAME));
        assertTrue(HypixelSafetyManager.isDisallowed(NAME));
        assertFalse(HypixelSafetyManager.isAllowedOn(NAME, UNKNOWN));
    }

    @Test void allowedInSingleplayer() {
        server = SINGLE;
        assertEquals(Block.NONE, HypixelSafetyManager.blockOf(NAME));
        assertTrue(HypixelSafetyManager.isAllowed(NAME));
    }

    @Test void allowedOnOwnServer() {
        server = OWN;
        assertEquals(Block.NONE, HypixelSafetyManager.blockOf(NAME));
        assertTrue(HypixelSafetyManager.isAllowed(NAME));
    }

    @Test void allowedAfterPerServerBypassOnly() {
        server = UNKNOWN;
        assertTrue(HypixelSafetyManager.isDisallowed(NAME));
        HypixelSafetyManager.bypass(NAME);
        assertTrue(HypixelSafetyManager.isBypassed(NAME));
        assertTrue(HypixelSafetyManager.isAllowed(NAME));
        assertTrue(HypixelSafetyManager.isAllowedOn(NAME, UNKNOWN));
        server = "other.example.org"; // the bypass is per server
        assertTrue(HypixelSafetyManager.isDisallowed(NAME));
    }

    @Test void instantHotbarAndSmoothSingleplayerAreNeverRestricted() {
        for (String module : new String[] {InstantHotbarModule.NAME, SmoothSingleplayerModule.NAME}) {
            for (String address : new String[] {HYPIXEL, UNKNOWN, OWN, SINGLE}) {
                server = address;
                assertEquals(Block.NONE, HypixelSafetyManager.blockOf(module), module + " on " + address);
                assertFalse(HypixelSafetyManager.isDisallowed(module), module + " on " + address);
                assertEquals(Block.NONE, HypixelSafetyManager.blockOf(module, address), module + " by address " + address);
            }
        }
    }
}
