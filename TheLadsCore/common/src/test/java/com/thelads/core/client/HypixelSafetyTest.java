package com.thelads.core.client;

import com.thelads.core.client.bridge.DefaultGameBridge;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.OldAnimationsModule;
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
        assertTrue(HypixelSafetyManager.isSingleplayerOnlyModule("StackRefill"), "Stack Refill is off on every server, Hypixel too");

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

    /** Minemen.club banned a player for Stack Refill's hidden inventory clicks: it and use-while-mining stay in singleplayer. */
    @Test
    public void testSingleplayerOnlyOnEveryServer() {
        TestGameBridge bridge = new TestGameBridge();
        LadsGameBridge.set(bridge);
        Module refill = new Module("StackRefill", "Stack Refill");
        refill.setEnabled(true);
        OldAnimationsModule anims = (OldAnimationsModule) ModuleManager.getInstance().getModule(OldAnimationsModule.NAME);
        boolean animsWas = anims.isEnabled();
        anims.setEnabled(true);
        try {
            assertTrue(refill.isEnabled());
            assertTrue(anims.useWhileMining(OldAnimations.Use.BLOCK));
            for (String server : new String[]{"minemen.club", "mc.hypixel.net", "Unknown"}) {
                bridge.setServer(server);
                assertTrue(refill.isBlockedByServer(), server);
                assertFalse(refill.isEnabled(), server);
                assertFalse(anims.useWhileMining(OldAnimations.Use.BLOCK), server);
            }
            bridge.setServer("Singleplayer");
            assertTrue(refill.isEnabled(), "back in singleplayer the saved state returns");
        } finally {
            anims.setEnabled(animsWas);
        }
    }

    /** Big PvP/minigame networks ban minimaps (or their radar): Xaero's minimap and world map stay off there, nowhere else. */
    @Test
    public void testMinimapOffOnNetworksThatBanIt() {
        for (String server : new String[]{"as.minemen.club", "na.minemen.club:25565", "mc.hypixel.net", "play.cubecraft.net", "play.mccisland.net",
                "hoplite.gg", "pvp.land", "play.pika-network.net", "blocksmc.com", "MINEMEN.CLUB."}) {
            assertTrue(HypixelSafetyManager.isMinimapBannedAddress(server), server);
        }
        for (String server : new String[]{"Singleplayer", "localhost", "play.wynncraft.com", "minemen.club.example.com", "notminemen.club", ""}) {
            assertFalse(HypixelSafetyManager.isMinimapBannedAddress(server), server);
        }
        TestGameBridge bridge = new TestGameBridge();
        LadsGameBridge.set(bridge);
        Module minimap = new Module("Minimap", "Minimap"), worldMap = new Module("XaeroWorldMap", "World map"), jade = new Module("Jade", "Jade");
        minimap.setEnabled(true);
        worldMap.setEnabled(true);
        jade.setEnabled(true);
        bridge.setServer("as.minemen.club");
        assertTrue(HypixelSafetyManager.isMinimapBanned());
        assertFalse(minimap.isEnabled());
        assertFalse(worldMap.isEnabled());
        assertTrue(jade.isEnabled(), "only the maps: Jade is a Hypixel-only block");
        assertFalse(HypixelSafetyManager.getBlockedNotice("Minimap").contains("Hypixel"), "the notice names no wrong server");
        bridge.setServer("play.wynncraft.com");
        assertFalse(HypixelSafetyManager.isMinimapBanned());
        assertTrue(minimap.isEnabled());
        assertTrue(worldMap.isEnabled());
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

    @Test
    public void testCachedHypixelAddress() {
        TestGameBridge bridge = new TestGameBridge();
        LadsGameBridge.set(bridge);

        bridge.setServer("mc.hypixel.net:25565");
        assertTrue(HypixelSafetyManager.isHypixel());
        assertTrue(HypixelSafetyManager.isHypixelAddress("mc.hypixel.net:25565"));
        // Second call hits cache
        assertTrue(HypixelSafetyManager.isHypixel());
        assertTrue(HypixelSafetyManager.isHypixelAddress("mc.hypixel.net:25565"));

        bridge.setServer("pvp.customserver.net");
        assertFalse(HypixelSafetyManager.isHypixel());
        assertFalse(HypixelSafetyManager.isHypixelAddress("pvp.customserver.net"));
        // Second call hits cache
        assertFalse(HypixelSafetyManager.isHypixel());
        assertFalse(HypixelSafetyManager.isHypixelAddress("pvp.customserver.net"));

        // qaOverride forces isHypixel() even on non-Hypixel server
        HypixelSafetyManager.qaOverride = true;
        try {
            assertTrue(HypixelSafetyManager.isHypixel());
            // isHypixelAddress itself still correctly reports false for the address
            assertFalse(HypixelSafetyManager.isHypixelAddress("pvp.customserver.net"));
        } finally {
            HypixelSafetyManager.qaOverride = false;
        }
    }
}
