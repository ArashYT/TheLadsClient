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

        // Risky module set under Anti-Ban rule
        assertTrue(HypixelSafetyManager.isRiskyModule("AutoReconnect"));
        assertTrue(HypixelSafetyManager.isRiskyModule("MouseTweaks"));
        assertTrue(HypixelSafetyManager.isRiskyModule("ReachDisplay"));
        assertTrue(HypixelSafetyManager.isRiskyModule("Jade"));
        assertTrue(HypixelSafetyManager.isRiskyModule("ShulkerBoxUtils"));
        assertTrue(HypixelSafetyManager.isRiskyModule("Fullbright"));
        assertTrue(HypixelSafetyManager.isRiskyModule(HypixelSafetyManager.TNT_THROUGH_BLOCKS));
        assertTrue(HypixelSafetyManager.isRiskyModule("StackRefill"));
        assertTrue(HypixelSafetyManager.isRiskyModule("Minimap"));
        assertTrue(HypixelSafetyManager.isRiskyModule("XaeroMinimap"));
        assertTrue(HypixelSafetyManager.isRiskyModule("XaeroWorldMap"));
        assertTrue(HypixelSafetyManager.isRiskyModule("SkyBlock Suite"));
        assertTrue(HypixelSafetyManager.isRiskyModule("Server Notification Actions"));
        assertTrue(HypixelSafetyManager.isRiskyModule("DungeonMap"));
        assertTrue(HypixelSafetyManager.isRiskyModule("SkyBlockTimers"));
        assertFalse(HypixelSafetyManager.isRiskyModule("FPS"));
        assertFalse(HypixelSafetyManager.isRiskyModule("CPS"));
        assertFalse(HypixelSafetyManager.isRiskyModule("Coordinates"));
        assertFalse(HypixelSafetyManager.isRiskyModule("Keystrokes"));
        assertFalse(HypixelSafetyManager.isRiskyModule("ArmorHUD"));
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
        assertFalse(jade.isEnabled(), "Jade is in the risky set: off on Minemen");
        assertEquals(HypixelSafetyManager.Block.BANNED, HypixelSafetyManager.blockOf("Jade"));
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
            assertEquals(HypixelSafetyManager.Block.UNKNOWN, HypixelSafetyManager.blockOf("Jade"), server);
            assertFalse(jade.isEnabled(), server);
            assertTrue(HypixelSafetyManager.getBlockedNotice("StackRefill").contains("don't know"), server);
            assertTrue(HypixelSafetyManager.getBlockedNotice("Jade").contains("don't know"), server);
            for (String risky : new String[]{"AutoReconnect", "MouseTweaks", "ReachDisplay", "ShulkerBoxUtils", "Fullbright", HypixelSafetyManager.TNT_THROUGH_BLOCKS}) {
                assertEquals(HypixelSafetyManager.Block.UNKNOWN, HypixelSafetyManager.blockOf(risky), server + ": " + risky);
            }
        }
        bridge.setServer("as.minemen.club");
        assertEquals(HypixelSafetyManager.Block.BANNED, HypixelSafetyManager.blockOf("StackRefill"));
        assertEquals(HypixelSafetyManager.Block.BANNED, HypixelSafetyManager.blockOf("XaeroWorldMap"));
        for (String trusted : new String[]{"Singleplayer", "localhost:25565", "127.0.0.1", "10.0.0.7", "172.20.1.1", "[::1]:25565",
                "nikzerosmp.my.pebble.host", "smp.arashyt.ca", "arisesmp.arashyt.ca", "dev.arashyt.ca:25565", "2b2t.org"}) { // Lads SMP Reborn's servers.dat entries
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

    /**
     * Fullbright is in the risky set (default OFF on unknown servers for least ban risk),
     * but allowed on servers whose rules explicitly permit it (Hypixel, CubeCraft, MCC Island, Pika).
     */
    @Test
    public void testFullbrightServerGating() {
        TestGameBridge bridge = new TestGameBridge();
        LadsGameBridge.set(bridge);
        Module fullbright = new Module("Fullbright", "Fullbright");
        fullbright.setEnabled(true);

        // Allowed on servers with explicit rules:
        // Hypixel: https://hypixel.net/rules/#mods (Brightness & Gamma Adjustment Modifications)
        bridge.setServer("mc.hypixel.net");
        assertEquals(HypixelSafetyManager.Block.NONE, HypixelSafetyManager.blockOf("Fullbright"));
        assertFalse(fullbright.isBlockedByServer());
        assertTrue(fullbright.isEnabled());

        // CubeCraft: https://www.cubecraft.net/rules/ (Allowed Utility Mods - Gamma/Brightness mods)
        bridge.setServer("play.cubecraft.net");
        assertEquals(HypixelSafetyManager.Block.NONE, HypixelSafetyManager.blockOf("Fullbright"));
        assertFalse(fullbright.isBlockedByServer());
        assertTrue(fullbright.isEnabled());

        // MCC Island: https://mccisland.net/community-rules (Approved Mods - Gamma/Brightness adjustments)
        bridge.setServer("play.mccisland.net");
        assertEquals(HypixelSafetyManager.Block.NONE, HypixelSafetyManager.blockOf("Fullbright"));
        assertFalse(fullbright.isBlockedByServer());
        assertTrue(fullbright.isEnabled());

        // PikaNetwork: https://pika-network.net/rules/ (Allowed Modifications - brightness adjustments / fullbright)
        bridge.setServer("play.pika-network.net");
        assertEquals(HypixelSafetyManager.Block.NONE, HypixelSafetyManager.blockOf("Fullbright"));
        assertFalse(fullbright.isBlockedByServer());
        assertTrue(fullbright.isEnabled());

        // GommeHD explicitly bans Fullbright: https://www.gommehd.net/rules-minecraft
        bridge.setServer("gommehd.net");
        assertEquals(HypixelSafetyManager.Block.BANNED, HypixelSafetyManager.blockOf("Fullbright"));
        assertTrue(fullbright.isBlockedByServer());
        assertFalse(fullbright.isEnabled());

        // Minemen Club bans unapproved modifications
        bridge.setServer("as.minemen.club");
        assertEquals(HypixelSafetyManager.Block.BANNED, HypixelSafetyManager.blockOf("Fullbright"));
        assertTrue(fullbright.isBlockedByServer());
        assertFalse(fullbright.isEnabled());

        // Unknown servers default to Block.UNKNOWN (least ban chance)
        bridge.setServer("play.somesmp.net");
        assertEquals(HypixelSafetyManager.Block.UNKNOWN, HypixelSafetyManager.blockOf("Fullbright"));
        assertTrue(fullbright.isBlockedByServer());
        assertFalse(fullbright.isEnabled());

        // Bypassable via red warning on unknown server
        HypixelSafetyManager.bypass("Fullbright");
        assertTrue(HypixelSafetyManager.isBypassed("Fullbright"));
        assertFalse(fullbright.isBlockedByServer());
        assertTrue(fullbright.isEnabled());

        // Singleplayer / trusted
        bridge.setServer("Singleplayer");
        assertEquals(HypixelSafetyManager.Block.NONE, HypixelSafetyManager.blockOf("Fullbright"));
        assertFalse(fullbright.isBlockedByServer());
        assertTrue(fullbright.isEnabled());
    }

    /**
     * TNT timer Through-blocks is gated by server rules (ESP-like info through walls is banned on Hypixel/Minemen
     * and off on unknown servers).
     */
    @Test
    public void testTntTimerThroughBlocksGating() {
        TestGameBridge bridge = new TestGameBridge();
        LadsGameBridge.set(bridge);
        com.thelads.core.modules.TntTimerModule tnt = new com.thelads.core.modules.TntTimerModule();
        tnt.throughBlocks.set(true);

        // Singleplayer: allowed
        bridge.setServer("Singleplayer");
        assertEquals(HypixelSafetyManager.Block.NONE, HypixelSafetyManager.blockOf(HypixelSafetyManager.TNT_THROUGH_BLOCKS));
        assertTrue(tnt.seeThrough());

        // Hypixel: ESP through walls is banned
        bridge.setServer("mc.hypixel.net");
        assertEquals(HypixelSafetyManager.Block.BANNED, HypixelSafetyManager.blockOf(HypixelSafetyManager.TNT_THROUGH_BLOCKS));
        assertFalse(tnt.seeThrough());

        // Minemen: banned
        bridge.setServer("as.minemen.club");
        assertEquals(HypixelSafetyManager.Block.BANNED, HypixelSafetyManager.blockOf(HypixelSafetyManager.TNT_THROUGH_BLOCKS));
        assertFalse(tnt.seeThrough());

        // Unknown server: Block.UNKNOWN
        bridge.setServer("play.somesmp.net");
        assertEquals(HypixelSafetyManager.Block.UNKNOWN, HypixelSafetyManager.blockOf(HypixelSafetyManager.TNT_THROUGH_BLOCKS));
        assertFalse(tnt.seeThrough());

        // Bypass on unknown server
        HypixelSafetyManager.bypass(HypixelSafetyManager.TNT_THROUGH_BLOCKS);
        assertTrue(tnt.seeThrough());
        HypixelSafetyManager.removeBypass(HypixelSafetyManager.TNT_THROUGH_BLOCKS);
        assertFalse(tnt.seeThrough());
    }

    /** parity-1023: every new feature that shows what vanilla hides or acts for the player stays in the risky set (anti-ban rule). */
    @Test
    void parityGatedModulesAreRisky() {
        for (String name : new String[] {"BlockInfo", "Freelook", "FogClearView", "HitboxesThroughWalls", "Light Overlay", "Loot Beams",
                "Mob Overlay", "Mob Size", "Fullbright", "Jade", "AutoReconnect", "Breeding Timer", "Ender Chest Peek",
                "Unlimited Command Length", "Auto Text", "Inventory Move", "Armor Swap",
                "Container Sorting", "Elytra Swap", "Creative Fly Boost", "Schematic", "Schematic Visualizer", "Schematica", "Opened Chest Highlight"})
            assertTrue(HypixelSafetyManager.isRiskyModule(name), name + " must be gated");
        assertFalse(HypixelSafetyManager.isRiskyModule("Block Overlay"), "pure visuals stay ungated");
        for (String name : new String[] {"Chat", "Slot Lock", "Inventory Search"}) // chat lane: client-side only, nothing extra sent
            assertFalse(HypixelSafetyManager.isRiskyModule(name), name + " stays ungated");
    }

    /** 1.12.0 anti-ban: Survival Tweaks' dig-speed and movement options act only where a server can't flag them; the rest stay on. */
    @Test
    void survivalMechanicsOnlyWhereTheServerRulesAllow() {
        TestGameBridge bridge = new TestGameBridge();
        LadsGameBridge.set(bridge);
        com.thelads.core.modules.SurvivalTweaksModule tweaks = com.thelads.core.modules.SurvivalTweaksModule.get();
        assertTrue(tweaks.isEnabled());
        for (String allowed : new String[]{"Singleplayer", "192.168.1.20:25565", "smp.arashyt.ca"}) {
            bridge.setServer(allowed);
            assertTrue(tweaks.isFasterObsidianMiningEnabled() && tweaks.isBerryBushProtectionEnabled() && tweaks.isElytraWaterSwimEnabled(), allowed);
        }
        for (String server : new String[]{"mc.hypixel.net", "as.minemen.club", "play.somesmp.net", "Unknown"}) {
            bridge.setServer(server);
            assertFalse(tweaks.isFasterObsidianMiningEnabled(), server + ": FastBreak");
            assertFalse(tweaks.isBerryBushProtectionEnabled(), server + ": movement in bushes");
            assertFalse(tweaks.isElytraWaterSwimEnabled(), server + ": elytra state");
            assertTrue(tweaks.isEnabled() && tweaks.isBoat360ViewEnabled() && tweaks.isLongerChatHistoryEnabled() && tweaks.isFastIpPingEnabled()
                && tweaks.isKeepBarsWhileRidingEnabled() && tweaks.isCreeperFullDropsEnabled(), server + ": the rest of the module stays on");
        }
        bridge.setServer("play.somesmp.net");
        HypixelSafetyManager.bypass(com.thelads.core.modules.SurvivalTweaksModule.MECHANICS);
        assertTrue(tweaks.isFasterObsidianMiningEnabled(), "bypassed for this server");
    }

    /** Dimension brightness past vanilla's Bright is a Fullbright: only where Fullbright is allowed; darker or up to Bright everywhere. */
    @Test
    void dimensionBrightnessFollowsTheFullbrightRules() {
        TestGameBridge bridge = new TestGameBridge();
        LadsGameBridge.set(bridge);
        com.thelads.core.modules.SurvivalTweaksModule tweaks = com.thelads.core.modules.SurvivalTweaksModule.get();
        double was = tweaks.dimensionBrightness.getValue();
        try {
            tweaks.dimensionBrightness.setValue(200);
            for (String server : new String[]{"Singleplayer", "mc.hypixel.net"}) { // local, and a server whose rules allow Fullbright
                bridge.setServer(server);
                assertEquals(2.0f, tweaks.brightness(1.0f), 1e-6f, server);
            }
            for (String server : new String[]{"gommehd.net", "play.somesmp.net"}) { // bans Fullbright / unknown rules
                bridge.setServer(server);
                assertEquals(1.0f, tweaks.brightness(1.0f), 1e-6f, server + ": vanilla's Bright at most");
                assertEquals(0.8f, tweaks.brightness(0.4f), 1e-6f, server + ": brighter up to Bright is vanilla too");
                assertEquals(3.0f, tweaks.brightness(3.0f), 1e-6f, server + ": a gamma the player set in options.txt is not cut");
            }
            tweaks.dimensionBrightness.setValue(50);
            assertEquals(0.5f, tweaks.brightness(1.0f), 1e-6f, "darker is fine everywhere");
        } finally {
            tweaks.dimensionBrightness.setValue(was);
        }
    }
}
