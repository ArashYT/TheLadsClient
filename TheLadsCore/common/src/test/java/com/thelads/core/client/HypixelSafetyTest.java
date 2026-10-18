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
        private boolean ingame = true;

        public void setServer(String s) { this.server = s; }

        @Override public String getServerAddress() { return server; }
        @Override public boolean isIngame() { return ingame; }
    }

    @org.junit.jupiter.api.io.TempDir
    java.nio.file.Path tmp;

    @org.junit.jupiter.api.BeforeEach
    public void isolateBypasses() {
        HypixelSafetyManager.setBypassFile(tmp.resolve("server_bypasses.json").toFile());
    }

    @AfterEach
    public void resetBridge() {
        LadsGameBridge.set(new DefaultGameBridge());
        HypixelSafetyManager.setBypassFile(null);
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
        bridge.setServer("192.168.1.20:25565"); // a LAN world: no network anticheat
        assertFalse(HypixelSafetyManager.isMinimapBanned());
        assertTrue(minimap.isEnabled());
        assertTrue(worldMap.isEnabled());
    }

    /** Owner rule: on a server whose rules we don't know, the risky modules start off (bypassable), the rest stay on. */
    @Test
    public void testUnknownServerBlocksRiskyModules() {
        TestGameBridge bridge = new TestGameBridge();
        LadsGameBridge.set(bridge);
        Module minimap = new Module("XaeroMinimap", "Minimap"), refill = new Module("StackRefill", "Stack Refill"), jade = new Module("Jade", "Jade");
        minimap.setEnabled(true);
        refill.setEnabled(true);
        jade.setEnabled(true);
        for (String server : new String[]{"play.somesmp.net", "Unknown", "45.12.3.4:25565"}) {
            bridge.setServer(server);
            assertEquals(HypixelSafetyManager.Block.UNKNOWN, HypixelSafetyManager.blockOf("XaeroMinimap"), server);
            assertEquals(HypixelSafetyManager.Block.UNKNOWN, HypixelSafetyManager.blockOf("StackRefill"), server);
            assertFalse(minimap.isEnabled(), server);
            assertFalse(refill.isEnabled(), server);
            assertTrue(jade.isEnabled(), "Jade is only a Hypixel block: " + server);
            assertTrue(HypixelSafetyManager.getBlockedNotice("StackRefill").contains("don't know"), server);
        }
        bridge.setServer("as.minemen.club");
        assertEquals(HypixelSafetyManager.Block.BANNED, HypixelSafetyManager.blockOf("StackRefill"));
        assertEquals(HypixelSafetyManager.Block.BANNED, HypixelSafetyManager.blockOf("XaeroWorldMap"));
        for (String trusted : new String[]{"Singleplayer", "localhost:25565", "127.0.0.1", "10.0.0.7", "172.20.1.1", "[::1]:25565",
                "nikzerosmp.my.pebble.host", "smp.arashyt.ca"}) {
            bridge.setServer(trusted);
            assertEquals(HypixelSafetyManager.Block.NONE, HypixelSafetyManager.blockOf("StackRefill"), trusted);
            assertTrue(minimap.isEnabled(), trusted);
            assertTrue(refill.isEnabled(), trusted);
            assertTrue(HypixelSafetyManager.isTrustedServer(), trusted);
        }
        bridge.setServer("172.32.0.1"); // outside 172.16/12: a public address
        assertFalse(refill.isEnabled());
        bridge.setServer("notarashyt.ca");
        assertFalse(refill.isEnabled(), "only arashyt.ca and its subdomains are ours");
        bridge.setServer("Unknown");
        bridge.ingame = false; // 1.20.1's main menu reports "Unknown": nothing to ban outside a world
        assertTrue(refill.isEnabled());
        assertEquals(HypixelSafetyManager.Block.NONE, HypixelSafetyManager.blockOf("XaeroMinimap"));
    }

    /** The red warning's bypass: per server, minimap + world map together, saved to its own file, undoable, never in the Hypixel QA. */
    @Test
    public void testBypassPerServer() {
        TestGameBridge bridge = new TestGameBridge();
        LadsGameBridge.set(bridge);
        Module minimap = new Module("XaeroMinimap", "Minimap"), worldMap = new Module("XaeroWorldMap", "World map"), refill = new Module("StackRefill", "Stack Refill");
        minimap.setEnabled(true);
        worldMap.setEnabled(true);
        refill.setEnabled(true);
        bridge.setServer("Play.SomeSMP.net:25565");
        assertFalse(minimap.isEnabled());
        HypixelSafetyManager.bypass("Minimap");
        assertTrue(minimap.isEnabled(), "bypassed here");
        assertTrue(worldMap.isEnabled(), "one bypass for both maps");
        assertFalse(refill.isEnabled(), "other modules keep the rules");
        assertTrue(HypixelSafetyManager.isBypassed("XaeroMinimap"));
        assertEquals(HypixelSafetyManager.Block.UNKNOWN, HypixelSafetyManager.blockOf("XaeroMinimap"), "the block reason stays visible");

        bridge.setServer("play.somesmp.net"); // same host, other spelling
        assertTrue(minimap.isEnabled());
        bridge.setServer("other.server.net");
        assertFalse(minimap.isEnabled(), "a bypass is for one server only");

        bridge.setServer("play.somesmp.net");
        HypixelSafetyManager.setBypassFile(tmp.resolve("server_bypasses.json").toFile()); // forget, then reload from disk
        assertTrue(minimap.isEnabled(), "kept across restarts");

        HypixelSafetyManager.qaOverride = true;
        try {
            assertFalse(minimap.isEnabled(), "the Hypixel QA ignores bypasses");
        } finally {
            HypixelSafetyManager.qaOverride = false;
        }

        HypixelSafetyManager.removeBypass("XaeroWorldMap");
        assertFalse(minimap.isEnabled(), "undo: the rules apply again");
        assertFalse(HypixelSafetyManager.isBypassed("Minimap"));

        bridge.setServer("Singleplayer");
        HypixelSafetyManager.bypass("StackRefill"); // nothing to bypass in a local world
        bridge.setServer("play.somesmp.net");
        assertFalse(refill.isEnabled());
    }

    /** A server block is not the player's choice: saving the config on a blocking server keeps the module's own on/off. */
    @Test
    public void testConfigSaveKeepsBlockedModulesOn() {
        TestGameBridge bridge = new TestGameBridge();
        LadsGameBridge.set(bridge);
        Module refill = ModuleManager.getInstance().getModule("StackRefill");
        assertNotNull(refill);
        boolean was = refill.isEnabledSetting();
        refill.setEnabled(true);
        try {
            bridge.setServer("play.somesmp.net");
            assertFalse(refill.isEnabled());
            assertTrue(com.thelads.core.config.ConfigManager.toJson().getAsJsonObject("modules").getAsJsonObject("StackRefill").get("enabled").getAsBoolean());
        } finally {
            refill.setEnabled(was);
        }
    }

    @Test
    public void testHostParsing() {
        assertEquals("play.example.net", HypixelSafetyManager.host(" Play.Example.NET.:25565 "));
        assertEquals("::1", HypixelSafetyManager.host("[::1]:25565"));
        assertEquals("2001:db8::1", HypixelSafetyManager.host("2001:db8::1"));
        assertEquals("1.2.3.4", HypixelSafetyManager.host("1.2.3.4"));
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
