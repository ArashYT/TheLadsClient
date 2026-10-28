package com.thelads.core.client.hypixel;

import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.client.bridge.DefaultGameBridge;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class HypixelParity1023Test {

    private TestBridge bridge;

    private static class TestBridge extends DefaultGameBridge {
        final List<String> chatMessages = new ArrayList<>();
        int comboSoundCount = 0;
        int hotbarBlocks = 0;
        String serverAddress = "mc.hypixel.net";

        @Override
        public String getServerAddress() {
            return serverAddress;
        }

        @Override
        public void displayChatMessage(String text) {
            chatMessages.add(text);
        }

        @Override
        public void playComboSound(float pitch, float volume) {
            comboSoundCount++;
        }

        @Override
        public int getHotbarBlockCount() {
            return hotbarBlocks;
        }

        @Override
        public boolean isIngame() {
            return true;
        }
    }

    @BeforeEach
    public void setUp() {
        bridge = new TestBridge();
        bridge.serverAddress = "mc.hypixel.net";
        LadsGameBridge.set(bridge);
        HypixelSafetyManager.qaOverride = true;
        HypixelMods.resetForTest();
HypixelMods.tick(bridge, new Object(), new Object(), 0L);

        Module m = ModuleManager.getInstance().getModule(HypixelMods.HYPIXEL_MODS);
        if (m != null) {
            m.getOptions().forEach(com.thelads.core.config.Option::reset);
            m.setEnabled(true);
        }

        // Verify that all features default to OFF (false)
        assertFalse(HypixelMods.on(HypixelMods.AUTO_GL), "AUTO_GL must default OFF");
        assertFalse(HypixelMods.on(HypixelMods.PIT_LAG), "PIT_LAG must default OFF");
        assertFalse(HypixelMods.on(HypixelMods.OPENED_CHESTS), "OPENED_CHESTS must default OFF");
        assertFalse(HypixelMods.on(HypixelMods.LOW_BLOCK), "LOW_BLOCK must default OFF");
        assertFalse(HypixelMods.on(HypixelMods.SILENT_LOBBY), "SILENT_LOBBY must default OFF");
        assertFalse(HypixelMods.on(HypixelMods.BW_EXTRAS), "BW_EXTRAS must default OFF");
    }

    @AfterEach
    public void tearDown() {
        HypixelSafetyManager.qaOverride = false;
        HypixelMods.resetForTest();
HypixelMods.tick(bridge, new Object(), new Object(), 0L);
        LadsGameBridge.set(new DefaultGameBridge());
    }

    private void enableOption(String name) {
        Module m = ModuleManager.getInstance().getModule(HypixelMods.HYPIXEL_MODS);
        if (m != null && m.getOption(name) instanceof BoolOption b) {
            b.set(true);
        }
    }

    @Test
    public void testAutoGl() {
        enableOption(HypixelMods.AUTO_GL);
        assertTrue(HypixelMods.on(HypixelMods.AUTO_GL));

        // When game starts countdown finishes
        HypixelMods.chat("The game starts in 1 second!", 1000L);
        assertEquals("/ac glhf", HypixelMods.nextQueuedCommandForTest());
    }

    @Test
    public void testPitSpawnCrowdPlayerHider() {
        enableOption(HypixelMods.PIT_LAG);
        assertTrue(HypixelMods.on(HypixelMods.PIT_LAG));

        HypixelMods.setLocationForTest("PIT", "pit");

        // Other player at spawn height Y=115 should be hidden
        assertTrue(CrowdPlayerHider.shouldHidePitPlayer(false, 0.0, 115.0, 0.0));

        // Other player in central spawn safe zone Y=98 should be hidden
        assertTrue(CrowdPlayerHider.shouldHidePitPlayer(false, 5.0, 98.0, 5.0));

        // Other player down in combat arena (Y=50) should NOT be hidden
        assertFalse(CrowdPlayerHider.shouldHidePitPlayer(false, 30.0, 50.0, 30.0));

        // Local player must NEVER be hidden anywhere
        assertFalse(CrowdPlayerHider.shouldHidePitPlayer(true, 0.0, 115.0, 0.0));

        // When in another game (e.g. Bed Wars), Pit hider must not hide
        HypixelMods.setLocationForTest("BEDWARS", "bedwars_four_four");
        assertFalse(CrowdPlayerHider.shouldHidePitPlayer(false, 0.0, 115.0, 0.0));
    }

    @Test
    public void testLowBlockWarning() {
        enableOption(HypixelMods.LOW_BLOCK);
        assertTrue(HypixelMods.on(HypixelMods.LOW_BLOCK));

        // Outside Bed Wars / Bridge, checkHotbar should not trigger
        bridge.hotbarBlocks = 10;
        LowBlockWarning.checkHotbar(bridge, "SKYWARS", "solo_normal", 16);
        assertEquals(0, bridge.comboSoundCount);
        assertFalse(LowBlockWarning.isTriggered());

        // In Bed Wars with 32 blocks (above threshold 16), does not trigger
        bridge.hotbarBlocks = 32;
        LowBlockWarning.checkHotbar(bridge, "BEDWARS", "bedwars_eight_one", 16);
        assertEquals(0, bridge.comboSoundCount);
        assertFalse(LowBlockWarning.isTriggered());

        // When blocks drop to 12 (<= 16), triggers vanilla sound and chat warning ONCE
        bridge.hotbarBlocks = 12;
        LowBlockWarning.checkHotbar(bridge, "BEDWARS", "bedwars_eight_one", 16);
        assertEquals(1, bridge.comboSoundCount);
        assertTrue(LowBlockWarning.isTriggered());
        assertTrue(bridge.chatMessages.stream().anyMatch(msg -> msg.contains("Low blocks warning")));

        // Calling tick again with 12 blocks does NOT repeat the sound (one-shot until reset)
        LowBlockWarning.checkHotbar(bridge, "BEDWARS", "bedwars_eight_one", 16);
        assertEquals(1, bridge.comboSoundCount);

        // When blocks go back up to 64, warning resets
        bridge.hotbarBlocks = 64;
        LowBlockWarning.checkHotbar(bridge, "BEDWARS", "bedwars_eight_one", 16);
        assertFalse(LowBlockWarning.isTriggered());

        // Dropping low again fires the warning again
        bridge.hotbarBlocks = 8;
        LowBlockWarning.checkHotbar(bridge, "BEDWARS", "bedwars_eight_one", 16);
        assertEquals(2, bridge.comboSoundCount);
        assertTrue(LowBlockWarning.isTriggered());
    }

    @Test
    public void testSilentLobby() {
        enableOption(HypixelMods.SILENT_LOBBY);
        assertTrue(HypixelMods.on(HypixelMods.SILENT_LOBBY));

        // When not in lobby, sound is not muted
        HypixelMods.setLocationForTest("BEDWARS", "bedwars_four_four");
        assertFalse(SilentLobby.shouldMuteSound("minecraft:entity.firework_rocket.blast"));

        // When in lobby, gadget fireworks/explosions/pet sounds are muted
        HypixelMods.setLocationForTest("MAIN", "lobby");
        assertTrue(SilentLobby.shouldMuteSound("minecraft:entity.firework_rocket.blast"));
        assertTrue(SilentLobby.shouldMuteSound("minecraft:entity.generic.explode"));
        assertTrue(SilentLobby.shouldMuteSound("minecraft:entity.bat.ambient"));
        assertTrue(SilentLobby.shouldMuteSound("minecraft:block.note_block.pling"));

        // Normal game sounds (like UI click or footsteps) are not muted
        assertFalse(SilentLobby.shouldMuteSound("minecraft:ui.button.click"));
        assertFalse(SilentLobby.shouldMuteSound("minecraft:block.stone.step"));
    }

    @Test
    public void testBedWarsExtras() {
        enableOption(HypixelMods.BW_EXTRAS);
        enableOption(HypixelMods.BW_EXTRAS + ": Hardcore Hearts");
        enableOption(HypixelMods.BW_EXTRAS + ": Bed Alerts");
        assertTrue(HypixelMods.on(HypixelMods.BW_EXTRAS));

        HypixelMods.setLocationForTest("BEDWARS", "bedwars_eight_two");

        // Before bed loss
        assertFalse(BedWarsExtras.isHardcoreHearts());

        // Bed destruction message for team
        BedWarsExtras.onChat("BED DESTRUCTION > Red Bed was destroyed by Steve!", 1_000_000_000L);
        assertNotNull(BedWarsExtras.getActiveAlert(1_000_000_000L));

        // Player's own bed destroyed
        BedWarsExtras.onChat("Your Bed was destroyed!", 2_000_000_000L);
        assertTrue(BedWarsExtras.isHardcoreHearts());

        // Reset on new game / world change
        BedWarsExtras.reset();
        assertFalse(BedWarsExtras.isHardcoreHearts());
        assertNull(BedWarsExtras.getActiveAlert(2_000_000_000L));
    }

    @Test
    public void testOpenedChestHighlight(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) {
        enableOption(HypixelMods.OPENED_CHESTS);
        assertTrue(HypixelMods.on(HypixelMods.OPENED_CHESTS));
        // Remembering opened chests shows what vanilla does not (risky set): off on Hypixel unless the player bypassed it there.
        assertFalse(OpenedChestHighlight.isEnabled());
        OpenedChestHighlight.recordChestOpened(10, 64, -20);
        assertEquals(0, OpenedChestHighlight.count());
        HypixelSafetyManager.qaOverride = false; // the Hypixel QA ignores bypasses: the real address (mc.hypixel.net)
        HypixelSafetyManager.setBypassFile(tmp.resolve("server_bypasses.json").toFile());
        try {
            HypixelSafetyManager.bypass(OpenedChestHighlight.NAME);
            openedChestsRecordAndClear();
        } finally {
            HypixelSafetyManager.setBypassFile(null);
        }
    }

    private void openedChestsRecordAndClear() {
        assertEquals(0, OpenedChestHighlight.count());
        assertFalse(OpenedChestHighlight.isOpened(10, 64, -20));

        OpenedChestHighlight.recordChestOpened(10, 64, -20);
        assertTrue(OpenedChestHighlight.isOpened(10, 64, -20));
        assertFalse(OpenedChestHighlight.isOpened(10, 65, -20));
        assertEquals(1, OpenedChestHighlight.count());

        // Clear on world change
        OpenedChestHighlight.clear();
        assertEquals(0, OpenedChestHighlight.count());
        assertFalse(OpenedChestHighlight.isOpened(10, 64, -20));
    }

    @Test
    public void testGatedRowsBlockedOnUnknownServers() {
        // Under QA override off (unknown server)
        HypixelSafetyManager.qaOverride = false;
        bridge.serverAddress = "play.unknown-server.net";

        // Gated Hypixel features: Auto GL, Opened Chest Highlight
        assertTrue(HypixelSafetyManager.isGatedHypixelModule("Auto GL"));
        assertTrue(HypixelSafetyManager.isGatedHypixelModule("Opened Chest Highlight"));

        // Verify they are blocked (marked RISKY / BANNED / UNKNOWN) on unknown servers
        assertNotEquals(HypixelSafetyManager.Block.NONE, HypixelSafetyManager.blockOf("Auto GL"));
        assertNotEquals(HypixelSafetyManager.Block.NONE, HypixelSafetyManager.blockOf("Opened Chest Highlight"));
    }
}
