package com.thelads.core.client.hypixel;

import static org.junit.jupiter.api.Assertions.*;

import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.client.bridge.DefaultGameBridge;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;
import com.thelads.core.config.TextOption;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for all 9 PARITY-1023 Hypixel lane features:
 * 1. Auto GL
 * 2. Level Head
 * 3. HyStats
 * 4. Crowd Player Hiding (Pit Lag Reducer & SkyBlock NPC hiding)
 * 5. Opened Chest Highlight
 * 6. Low Block Warning
 * 7. Silent Lobby
 * 8. Bed Wars Extras (hardcore hearts, alerts)
 * 9. SkyBlock Suite (dungeon score, composter, solvers, zealot counter, item lookup)
 */
class HypixelParity1023Test {
    private static final long S = 1_000_000_000L;
    private final List<String> sentChat = new ArrayList<>();
    private final List<String> displayedChat = new ArrayList<>();

    private final LadsGameBridge bridge = new DefaultGameBridge() {
        @Override public String getServerAddress() { return "mc.hypixel.net"; }
        @Override public void sendChatLine(String text) { sentChat.add(text); }
        @Override public void displayChatMessage(String text) { displayedChat.add(text); }
        @Override public String getPlayerName() { return "LocalPlayer"; }
    };

    private final Object connection = new Object();
    private final Object world = new Object();

    @BeforeEach
    void setUp() {
        LadsGameBridge.set(bridge);
        HypixelSafetyManager.qaOverride = true; // count as Hypixel
        Module m = ModuleManager.getInstance().getModule(HypixelMods.HYPIXEL_MODS);
        assertNotNull(m);
        m.setEnabled(true);
        HypixelMods.resetForTest();
        sentChat.clear();
        displayedChat.clear();
    }

    @AfterEach
    void tearDown() {
        HypixelSafetyManager.qaOverride = false;
        HypixelMods.tick(bridge, null, null, 0);
        LadsGameBridge.set(new DefaultGameBridge());
    }

    // --- 1. Auto GL ---
    @Test
    void testAutoGl() {
        Module m = ModuleManager.getInstance().getModule(HypixelMods.HYPIXEL_MODS);
        BoolOption autoGl = (BoolOption) m.getOption(HypixelMods.AUTO_GL);
        assertNotNull(autoGl);
        assertFalse(autoGl.get(), "Auto GL must be GATED and default OFF");

        autoGl.set(true);
        ((TextOption) m.getOption(HypixelMods.AUTO_GL + ": Message")).setValue("glhf!");
        ((SliderOption) m.getOption(HypixelMods.AUTO_GL + ": Delay seconds")).setValue(0);

        HypixelMods.tick(bridge, world, connection, 0);
        // Game start line
        HypixelMods.chat("The game starts in 1 second!", 1 * S);
        HypixelMods.tick(bridge, world, connection, 1 * S);

        assertTrue(sentChat.contains("/ac glhf!"), "Auto GL should send /ac glhf! upon game start countdown");
    }

    // --- 2. Level Head ---
    @Test
    void testLevelHeadCalculationsAndFormatting() {
        Module m = ModuleManager.getInstance().getModule(HypixelMods.HYPIXEL_MODS);
        BoolOption levelHead = (BoolOption) m.getOption(HypixelMods.LEVEL_HEAD);
        assertNotNull(levelHead);
        assertFalse(levelHead.get(), "Level Head must be GATED and default OFF");

        // Exp to Level official formula verification
        assertEquals(1, LevelHead.expToLevel(0));
        assertEquals(1, LevelHead.expToLevel(5000));
        assertEquals(2, LevelHead.expToLevel(10000));
        assertTrue(LevelHead.expToLevel(250000) > 10);

        // Bracket colors
        assertEquals("§7", LevelHead.getLevelColor(45));
        assertEquals("§f", LevelHead.getLevelColor(120));
        assertEquals("§6", LevelHead.getLevelColor(250));
        assertEquals("§b", LevelHead.getLevelColor(350));
        assertEquals("§2", LevelHead.getLevelColor(450));
        assertEquals("§4", LevelHead.getLevelColor(550));
        assertEquals("§6§l", LevelHead.getLevelColor(1200));

        // When disabled
        assertNull(LevelHead.formatLevelTag("Steve"));

        // Enable and format
        levelHead.set(true);
        LevelHead.setLevel("Steve", 250);
        String formatted = LevelHead.formatLevelTag("Steve");
        assertNotNull(formatted);
        assertTrue(formatted.contains("250") && formatted.contains("§6"), "Should contain level 250 with gold bracket color: " + formatted);
    }

    // --- 3. HyStats ---
    @Test
    void testHyStatsCommand() {
        Module m = ModuleManager.getInstance().getModule(HypixelMods.HYPIXEL_MODS);
        BoolOption hyStats = (BoolOption) m.getOption(HypixelMods.HY_STATS);
        assertNotNull(hyStats);
        assertFalse(hyStats.get(), "HyStats must be GATED and default OFF");

        hyStats.set(true);
        HyStats.setStats("Steve", new HyStats.PlayerStats(50, 10, 120, 30, 15, 90, 100, 20, "Legend"));

        // Intercepted locally: returns true
        assertTrue(HypixelMods.handleOutgoingCommand("/hystats Steve bedwars"));
        assertTrue(displayedChat.stream().anyMatch(line -> line.contains("Bed Wars") && line.contains("50 Wins")),
            "Should display formatted Bed Wars stats for Steve");

        // Non-matching command is not intercepted
        assertFalse(HypixelMods.handleOutgoingCommand("/help"));
    }

    // --- 4. Crowd Player Hiding ---
    @Test
    void testCrowdPlayerHider() {
        Module m = ModuleManager.getInstance().getModule(HypixelMods.HYPIXEL_MODS);
        BoolOption pitLag = (BoolOption) m.getOption(HypixelMods.PIT_LAG);
        assertNotNull(pitLag);
        assertTrue(pitLag.get(), "Pit Lag Reducer defaults to ON");

        // Local player is never hidden
        assertFalse(CrowdPlayerHider.shouldHidePlayer(true, 0, 120, 0));

        // In Pit spawn
        HypixelMods.tick(bridge, world, connection, 0);
        HypixelMods.chat("{\"server\":\"mini1P\",\"gametype\":\"PIT\",\"mode\":\"pit\"}", 0);

        // Player at spawn elevation (Y=115) should be hidden
        assertTrue(CrowdPlayerHider.shouldHidePlayer(false, 5, 115, 5));
        // Player down in the pit combat zone (Y=70) should NOT be hidden
        assertFalse(CrowdPlayerHider.shouldHidePlayer(false, 5, 70, 5));

        // SkyBlock NPC hiding
        BoolOption sbHide = (BoolOption) m.getOption(HypixelMods.SKYBLOCK_NPC_HIDE);
        sbHide.set(true);
        HypixelMods.chat("{\"server\":\"mini2S\",\"gametype\":\"SKYBLOCK\",\"mode\":\"hub\"}", 0);
        CrowdPlayerHider.registerNpc(10, 70, 10);

        // Player within radius (distance = 2) should be hidden
        assertTrue(CrowdPlayerHider.shouldHidePlayer(false, 11, 70, 11));
        // Player far away (distance = 30) should NOT be hidden
        assertFalse(CrowdPlayerHider.shouldHidePlayer(false, 40, 70, 40));
    }

    // --- 5. Opened Chest Highlight ---
    @Test
    void testOpenedChestHighlight() {
        Module m = ModuleManager.getInstance().getModule(HypixelMods.HYPIXEL_MODS);
        BoolOption openedChests = (BoolOption) m.getOption(HypixelMods.OPENED_CHESTS);
        assertNotNull(openedChests);
        assertFalse(openedChests.get(), "Opened Chest Highlight must be GATED and default OFF");

        openedChests.set(true);
        assertFalse(OpenedChestHighlight.isOpened(100, 65, -200));

        OpenedChestHighlight.onChestOpened(100, 65, -200);
        assertTrue(OpenedChestHighlight.isOpened(100, 65, -200));
        assertFalse(OpenedChestHighlight.isOpened(101, 65, -200));

        OpenedChestHighlight.clear();
        assertFalse(OpenedChestHighlight.isOpened(100, 65, -200));
    }

    // --- 6. Low Block Warning ---
    @Test
    void testLowBlockWarning() {
        Module m = ModuleManager.getInstance().getModule(HypixelMods.HYPIXEL_MODS);
        BoolOption lowBlock = (BoolOption) m.getOption(HypixelMods.LOW_BLOCK);
        assertNotNull(lowBlock);
        assertFalse(lowBlock.get(), "Low block warning defaults to OFF");

        lowBlock.set(true);
        ((SliderOption) m.getOption(HypixelMods.LOW_BLOCK + ": Threshold")).setValue(16);

        // Initial state at 32 blocks: no warning
        assertFalse(LowBlockWarning.checkBlocks(32, 0));
        // Drops to 10 blocks: triggers warning!
        assertTrue(LowBlockWarning.checkBlocks(10, 1 * S));
        assertTrue(displayedChat.stream().anyMatch(l -> l.contains("Low Blocks")), "Alert displayed in chat");

        // Stays at 9 blocks within cooldown: does not repeat
        assertFalse(LowBlockWarning.checkBlocks(9, 1500_000_000L));
    }

    // --- 7. Silent Lobby ---
    @Test
    void testSilentLobby() {
        Module m = ModuleManager.getInstance().getModule(HypixelMods.HYPIXEL_MODS);
        BoolOption silent = (BoolOption) m.getOption(HypixelMods.SILENT_LOBBY);
        assertNotNull(silent);
        assertTrue(silent.get(), "Silent Lobby defaults to ON");

        // Outside lobby: should not mute
        assertFalse(SilentLobby.shouldMuteSound("fireworks.blast"));

        // Enter lobby
        HypixelMods.tick(bridge, world, connection, 0);
        HypixelMods.chat("{\"server\":\"lobby12\",\"gametype\":\"MAIN\",\"mode\":\"lobby\"}", 0);

        assertTrue(SilentLobby.shouldMuteSound("fireworks.blast"));
        assertTrue(SilentLobby.shouldMuteSound("random.explode"));
        assertTrue(SilentLobby.shouldMuteSound("mob.bat.idle"));

        // Normal game sounds should NOT be muted
        assertFalse(SilentLobby.shouldMuteSound("step.stone"));
        assertFalse(SilentLobby.shouldMuteSound("game.player.hurt"));
    }

    // --- 8. Bed Wars Extras ---
    @Test
    void testBedWarsExtras() {
        Module m = ModuleManager.getInstance().getModule(HypixelMods.HYPIXEL_MODS);
        BoolOption bwExtras = (BoolOption) m.getOption(HypixelMods.BW_EXTRAS);
        assertNotNull(bwExtras);
        assertTrue(bwExtras.get(), "Bed Wars Extras defaults to ON");

        HypixelMods.tick(bridge, world, connection, 0);
        HypixelMods.chat("{\"server\":\"mini1B\",\"gametype\":\"BEDWARS\",\"mode\":\"bedwars_eight_one\"}", 0);

        // Initially bed not lost
        assertFalse(BedWarsExtras.isHardcoreHearts());

        // Enemy bed destroyed
        BedWarsExtras.onChat("BED DESTRUCTION > Red Bed was destroyed by Steve!", 1 * S);
        assertFalse(BedWarsExtras.isHardcoreHearts(), "Enemy bed destruction should not trigger hardcore hearts");
        assertTrue(displayedChat.stream().anyMatch(l -> l.contains("Red Bed was destroyed")), "Should show alert banner for bed destruction");

        // Player's bed destroyed
        BedWarsExtras.onChat("BED DESTRUCTION > Your Bed was destroyed by Steve!", 2 * S);
        assertTrue(BedWarsExtras.isHardcoreHearts(), "Losing your bed should activate hardcore hearts");
    }

    // --- 9. SkyBlock Suite ---
    @Test
    void testSkyBlockSuite() {
        Module m = ModuleManager.getInstance().getModule(HypixelMods.HYPIXEL_MODS);
        BoolOption sb = (BoolOption) m.getOption(HypixelMods.SKYBLOCK_SUITE);
        assertNotNull(sb);
        assertFalse(sb.get(), "SkyBlock Suite must be GATED and default OFF");

        sb.set(true);

        // Dungeon score
        SkyBlockSuite.updateDungeon(10, 10, 5, 0, 400);
        int score = SkyBlockSuite.calculateDungeonScore();
        assertTrue(score >= 270, "Max secrets and 0 deaths should achieve S/S+ rank: " + score);
        assertTrue(SkyBlockSuite.getDungeonScoreSummary().contains("S"));

        // Composter summary
        SkyBlockSuite.updateComposter(80, 90, 120);
        String comp = SkyBlockSuite.getComposterSummary();
        assertTrue(comp.contains("80%") && comp.contains("2m 00s"), "Composter summary: " + comp);

        // Zealot counter
        assertEquals(0, SkyBlockSuite.getZealotsSinceEye());
        SkyBlockSuite.onChat("You killed a Zealot!");
        assertEquals(1, SkyBlockSuite.getZealotsSinceEye());
        SkyBlockSuite.onChat("RARE DROP! (Summoning Eye)");
        assertEquals(0, SkyBlockSuite.getZealotsSinceEye());
        assertEquals(1, SkyBlockSuite.getTotalSummoningEyes());

        // Price lookup command
        assertTrue(HypixelMods.handleOutgoingCommand("/sbprice booster_cookie"));
        assertTrue(displayedChat.stream().anyMatch(l -> l.contains("booster_cookie")));
        assertTrue(displayedChat.stream().anyMatch(l -> l.contains("Lowest BIN")));

        // Solver slots
        SkyBlockSuite.setSolverSlots(List.of(12, 14, 16));
        assertTrue(SkyBlockSuite.isSolverSlot(14));
        assertFalse(SkyBlockSuite.isSolverSlot(15));
    }

    // --- Hypixel Safety Gating Test ---
    @Test
    void testGatedRowsBlockedOnUnknownServers() {
        HypixelSafetyManager.qaOverride = false; // Real safety checks

        LadsGameBridge testBridge = new DefaultGameBridge() {
            @Override public String getServerAddress() { return "unknown-server.net"; }
            @Override public boolean isIngame() { return true; }
        };
        LadsGameBridge.set(testBridge);

        // Gated Hypixel features must be blocked on unknown servers
        assertTrue(HypixelSafetyManager.isDisallowed("Auto GL"));
        assertTrue(HypixelSafetyManager.isDisallowed("Level Head"));
        assertTrue(HypixelSafetyManager.isDisallowed("HyStats"));
        assertTrue(HypixelSafetyManager.isDisallowed("Opened Chest Highlight"));
        assertTrue(HypixelSafetyManager.isDisallowed("SkyBlock Suite"));
    }
}
