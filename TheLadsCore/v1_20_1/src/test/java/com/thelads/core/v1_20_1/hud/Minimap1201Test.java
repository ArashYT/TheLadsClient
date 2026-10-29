package com.thelads.core.v1_20_1.hud;

import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.client.bridge.DefaultGameBridge;
import com.thelads.core.client.bridge.LadsGameBridge;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xaero.common.XaeroMinimapSession;
import xaero.common.minimap.MinimapProcessor;

import static org.junit.jupiter.api.Assertions.*;

public class Minimap1201Test {

    private static class TestGameBridge extends DefaultGameBridge {
        private String server = "Singleplayer";
        @Override public String getServerAddress() { return server; }
        @Override public boolean isIngame() { return true; }
        public void setServer(String s) { this.server = s; }
    }

    private TestGameBridge bridge;

    @BeforeEach
    public void setUp() {
        bridge = new TestGameBridge();
        LadsGameBridge.set(bridge);
        Minimap1201.resetForTests();
    }

    @AfterEach
    public void tearDown() {
        XaeroMinimapSession.setCurrentSession(null);
        LadsGameBridge.set(new DefaultGameBridge());
        Minimap1201.resetForTests();
    }

    @Test
    public void testFailClosedAssertsAndReassertsOnBannedServer() {
        var session = new XaeroMinimapSession();
        XaeroMinimapSession.setCurrentSession(session);
        MinimapProcessor proc = session.getMinimapProcessor();

        // On a banned server (e.g. Hypixel)
        bridge.setServer("mc.hypixel.net");
        assertTrue(HypixelSafetyManager.isMinimapBanned());
        assertFalse(proc.getNoMinimapMessageReceived());

        // First tick: sets noMinimapMessageReceived to true
        Minimap1201.failClosed();
        assertTrue(proc.getNoMinimapMessageReceived(), "failClosed must assert nominimap on banned server");
        assertFalse(Minimap1201.isFallbackFailed(), "fallbackFailed should not be latched");

        // Simulate Xaero clearing the flag on a reset chat code
        proc.setNoMinimapMessageReceived(false);

        // Next tick: re-asserts to true
        Minimap1201.failClosed();
        assertTrue(proc.getNoMinimapMessageReceived(), "failClosed must re-assert nominimap when cleared by server");
    }

    @Test
    public void testFailClosedDoesNotUndoOrOverrideWhenAllowed() {
        var session = new XaeroMinimapSession();
        XaeroMinimapSession.setCurrentSession(session);
        MinimapProcessor proc = session.getMinimapProcessor();

        // In Singleplayer (allowed)
        bridge.setServer("Singleplayer");
        assertFalse(HypixelSafetyManager.isMinimapBanned());

        // Minimap was off (e.g. server set it) - failClosed must NOT undo it (stateless)
        proc.setNoMinimapMessageReceived(true);
        Minimap1201.failClosed();
        assertTrue(proc.getNoMinimapMessageReceived(), "must not undo a server's nominimap");

        // Minimap was on - failClosed does not touch it
        proc.setNoMinimapMessageReceived(false);
        Minimap1201.failClosed();
        assertFalse(proc.getNoMinimapMessageReceived(), "must not switch off when minimap allowed");
    }

    @Test
    public void testFailClosedSurvivesNullSessionWithoutLatchingFailure() {
        bridge.setServer("mc.hypixel.net");
        XaeroMinimapSession.setCurrentSession(null);

        Minimap1201.failClosed();
        assertFalse(Minimap1201.isFallbackFailed(), "null session must not latch fallbackFailed");
    }
}
