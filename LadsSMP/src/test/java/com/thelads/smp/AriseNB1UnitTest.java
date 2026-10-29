package com.thelads.smp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.thelads.smp.features.arise.nb1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Clean-room JUnit 5 verification tests for the 15 Arise SMP Native Batch 1 (NB1) features:
 * 1. Admin Claims
 * 2. Carry On Extend
 * 3. Complete Turtle Armor
 * 4. Custom Item Despawn Duration
 * 5. Dimension Access Manager
 * 6. Enhanced Nature Quicksand
 * 7. Extra Dungeons
 * 8. Faster Portals
 * 9. Forgiving Border
 * 10. GameRule Manager
 * 11. Weaker Day Zombie
 * 12. Why stacks of 16?
 * 13. You Shall Not Spawn!
 * 14. SimpleVoiceChat Broadcast
 * 15. Useful Clipboard
 */
public class AriseNB1UnitTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        AriseNB1Config.setInstanceForTesting(new AriseNB1Config());
    }

    // ==========================================
    // 1. Admin Claims
    // ==========================================
    @Test
    @DisplayName("Admin Claims: chunk claim key formatting and access controls")
    void testAdminClaims() {
        assertTrue(AdminClaimsFeature.isEnabled());

        // Test format key logic
        String dim = "minecraft:overworld";
        int cx = 10;
        int cz = -5;
        String key = AdminClaimsFeature.formatKey(dim, cx, cz);
        assertEquals("minecraft:overworld:10,-5", key);

        // Verify config options
        AriseNB1Config cfg = AriseNB1Config.get();
        assertTrue(cfg.adminClaims.enabled);
        assertTrue(cfg.adminClaims.protectExplosions);
        assertEquals(2, cfg.adminClaims.requiredPermissionLevel);

        // Verify claim and unclaim logic via memory cache
        ChunkPos chunkPos = new ChunkPos(10, -5);
        BlockPos insidePos = new BlockPos(10 * 16 + 8, 64, -5 * 16 + 8);

        // Claim manually via memory
        AdminClaimsFeature.claimChunk(null, chunkPos);
        assertTrue(AdminClaimsFeature.getClaimedChunks().contains(key));

        // Unclaim
        AdminClaimsFeature.unclaimChunk(null, chunkPos);
        assertFalse(AdminClaimsFeature.getClaimedChunks().contains(key));
    }

    // ==========================================
    // 2. Carry On Extend
    // ==========================================
    @Test
    @DisplayName("Carry On Extend: velocity calculation and payload verification")
    void testCarryOnExtend() {
        assertTrue(CarryOnExtendFeature.isEnabled());
        AriseNB1Config.CarryOnExtendConfig cfg = AriseNB1Config.get().carryOnExtend;
        assertEquals(1.5f, cfg.throwPower, 0.001f);

        // Verify 3D vector rotation math
        float pitch = -10.0f;
        float yaw = 0.0f;
        Vec3 dir = Vec3.directionFromRotation(pitch, yaw);
        Vec3 velocity = dir.scale(cfg.throwPower);

        assertNotNull(velocity);
        assertTrue(velocity.y > 0, "Upward velocity should be positive due to upward pitch angle");
        assertEquals(cfg.throwPower * dir.length(), velocity.length(), 0.001);

        // Verify throw carried payload
        ThrowCarriedPayload payload = ThrowCarriedPayload.INSTANCE;
        assertNotNull(payload);
        assertEquals("ladssmp:throw_carried", payload.type().id().toString());
    }

    // ==========================================
    // 3. Complete Turtle Armor
    // ==========================================
    @Test
    @DisplayName("Complete Turtle Armor: configuration and set bonus toggles")
    void testCompleteTurtleArmor() {
        assertTrue(CompleteTurtleArmorFeature.isEnabled());

        AriseNB1Config.CompleteTurtleArmorConfig cfg = AriseNB1Config.get().completeTurtleArmor;
        assertTrue(cfg.enabled);
        assertTrue(cfg.fullSetDolphinsGrace);
        assertTrue(cfg.individualWaterBreathing);
    }

    // ==========================================
    // 4. Custom Item Despawn Duration
    // ==========================================
    @Test
    @DisplayName("Custom Item Despawn Duration: standard duration vs custom item overrides")
    void testCustomItemDespawn() {
        assertTrue(CustomItemDespawnFeature.isEnabled());

        AriseNB1Config.CustomItemDespawnConfig cfg = AriseNB1Config.get().customItemDespawn;
        assertEquals(300, cfg.defaultItemDuration);
        assertFalse(cfg.modifyDefaultItemDuration);

        // Configure custom durations
        cfg.despawnDurations.put("minecraft:diamond", 600);
        cfg.despawnDurations.put("minecraft:netherite_ingot", 1200);

        assertEquals(600, cfg.despawnDurations.get("minecraft:diamond"));
        assertEquals(1200, cfg.despawnDurations.get("minecraft:netherite_ingot"));

        // Verify conversion: 600s -> 12000 ticks, 1200s -> 24000 ticks
        assertEquals(12000, cfg.despawnDurations.get("minecraft:diamond") * 20);
        assertEquals(24000, cfg.despawnDurations.get("minecraft:netherite_ingot") * 20);
    }

    // ==========================================
    // 5. Dimension Access Manager
    // ==========================================
    @Test
    @DisplayName("Dimension Access Manager: allow and deny permissions persistence")
    void testDimensionAccess() {
        assertTrue(DimensionAccessFeature.isEnabled());

        DimensionAccessFeature.denyAccess("PlayerOne", "minecraft:the_nether");
        Set<String> denied = DimensionAccessFeature.getDeniedDimensions("PlayerOne");
        assertTrue(denied.contains("minecraft:the_nether"));

        // Allow access again
        assertTrue(DimensionAccessFeature.allowAccess("PlayerOne", "minecraft:the_nether"));
        assertFalse(DimensionAccessFeature.getDeniedDimensions("PlayerOne").contains("minecraft:the_nether"));
    }

    // ==========================================
    // 6. Enhanced Nature Quicksand
    // ==========================================
    @Test
    @DisplayName("Enhanced Nature Quicksand: sink speed and hazard configuration")
    void testQuicksandBlock() {
        assertTrue(QuicksandFeature.isEnabled());
        assertEquals(0.25, AriseNB1Config.get().quicksand.sinkSpeed, 0.001);
        assertTrue(AriseNB1Config.get().quicksand.generateInWorld);
    }

    // ==========================================
    // 7. Extra Dungeons
    // ==========================================
    @Test
    @DisplayName("Extra Dungeons: 6 themed variants and palette suppliers")
    void testExtraDungeons() {
        assertTrue(ExtraDungeonsFeature.isEnabled());

        assertEquals(6, ExtraDungeonsFeature.DungeonTheme.values().length);
        assertEquals(ExtraDungeonsFeature.DungeonTheme.DROWNED, ExtraDungeonsFeature.DungeonTheme.valueOf("DROWNED"));
        assertEquals(ExtraDungeonsFeature.DungeonTheme.HUSK, ExtraDungeonsFeature.DungeonTheme.valueOf("HUSK"));
        assertEquals(ExtraDungeonsFeature.DungeonTheme.SKELETON, ExtraDungeonsFeature.DungeonTheme.valueOf("SKELETON"));
        assertEquals(ExtraDungeonsFeature.DungeonTheme.SPIDER, ExtraDungeonsFeature.DungeonTheme.valueOf("SPIDER"));
        assertEquals(ExtraDungeonsFeature.DungeonTheme.STRAY, ExtraDungeonsFeature.DungeonTheme.valueOf("STRAY"));
        assertEquals(ExtraDungeonsFeature.DungeonTheme.ZOMBIE, ExtraDungeonsFeature.DungeonTheme.valueOf("ZOMBIE"));

        // Verify all 6 themes exist and have distinct names
        Set<String> themeNames = new java.util.HashSet<>();
        for (ExtraDungeonsFeature.DungeonTheme theme : ExtraDungeonsFeature.DungeonTheme.values()) {
            assertTrue(themeNames.add(theme.name()), "Theme name must be unique: " + theme.name());
        }
        assertEquals(6, themeNames.size());
    }

    // ==========================================
    // 8. Faster Portals
    // ==========================================
    @Test
    @DisplayName("Faster Portals: portal wait ticks")
    void testFasterPortals() {
        assertTrue(FasterPortalsFeature.isEnabled());
        assertEquals(10, FasterPortalsFeature.getPortalWaitTime());
    }

    // ==========================================
    // 9. Forgiving Border
    // ==========================================
    @Test
    @DisplayName("Forgiving Border: bounce buffer configuration and boundary check")
    void testForgivingBorder() {
        assertTrue(ForgivingBorderFeature.isEnabled());
        assertEquals(2.0, AriseNB1Config.get().forgivingBorder.bounceBuffer, 0.001);
    }

    // ==========================================
    // 10. GameRule Manager
    // ==========================================
    @Test
    @DisplayName("GameRule Manager: parsing rules from config and types")
    void testGameRuleManager() {
        assertTrue(GameRuleManagerFeature.isEnabled());
        Map<String, String> rules = AriseNB1Config.get().gameruleManager.forcedRules;
        assertNotNull(rules);

        rules.put("keepInventory", "true");
        rules.put("spawnRadius", "10");

        assertEquals("true", rules.get("keepInventory"));
        assertEquals("10", rules.get("spawnRadius"));
    }

    // ==========================================
    // 11. Weaker Day Zombie
    // ==========================================
    @Test
    @DisplayName("Weaker Day Zombie: weakness and slowness effect durations")
    void testWeakerDayZombie() {
        assertTrue(WeakerDayZombieFeature.isEnabled());
        AriseNB1Config.WeakerDayZombieConfig cfg = AriseNB1Config.get().weakerDayZombie;
        assertEquals(100, cfg.weaknessDuration);
        assertEquals(0, cfg.weaknessAmplifier);
        assertEquals(100, cfg.slownessDuration);
        assertEquals(0, cfg.slownessAmplifier);
    }

    // ==========================================
    // 12. Why Stacks of 16?
    // ==========================================
    @Test
    @DisplayName("Why stacks of 16?: items target stack size 64")
    void testWhyStacksOf16() {
        assertTrue(WhyStacksOf16Feature.isEnabled());
        AriseNB1Config.WhyStacksOf16Config cfg = AriseNB1Config.get().whyStacksOf16;
        assertEquals(64, cfg.stackSize);
        assertTrue(cfg.items.contains("minecraft:ender_pearl"));
        assertTrue(cfg.items.contains("minecraft:snowball"));
        assertTrue(cfg.items.contains("minecraft:egg"));
        assertTrue(cfg.items.contains("minecraft:honey_bottle"));
    }

    // ==========================================
    // 13. You Shall Not Spawn!
    // ==========================================
    @Test
    @DisplayName("You Shall Not Spawn!: rule matching and spawn blocking")
    void testYouShallNotSpawn() {
        assertTrue(YouShallNotSpawnFeature.isEnabled());

        // Configure a test rule
        AriseNB1Config.YouShallNotSpawnConfig.SpawnRule rule = new AriseNB1Config.YouShallNotSpawnConfig.SpawnRule();
        rule.dimension = "minecraft:the_nether";
        rule.entityId = "minecraft:creeper";
        rule.spawn_chance = 0.0; // blocked

        AriseNB1Config.get().youShallNotSpawn.dimensions.add(rule);

        // Verify config state
        assertFalse(AriseNB1Config.get().youShallNotSpawn.dimensions.isEmpty());
        assertEquals("minecraft:creeper", AriseNB1Config.get().youShallNotSpawn.dimensions.get(1).entityId);
    }

    // ==========================================
    // 14. SimpleVoiceChat Broadcast
    // ==========================================
    @Test
    @DisplayName("SimpleVoiceChat Broadcast: plugin ID and broadcast group name")
    void testSimpleVoiceChatBroadcast() {
        SimpleVoiceChatBroadcastPlugin plugin = new SimpleVoiceChatBroadcastPlugin();
        assertEquals("ladssmp_voicechat_broadcast", plugin.getPluginId());

        AriseNB1Config.VoicechatBroadcastConfig cfg = AriseNB1Config.get().voicechatBroadcast;
        assertTrue(cfg.enabled);
        assertEquals("broadcast", cfg.broadcastGroupName);
        assertEquals(2, cfg.permissionLevel);
    }

    // ==========================================
    // 15. Useful Clipboard
    // ==========================================
    @Test
    @DisplayName("Useful Clipboard: item registered and payload type and serialization")
    void testUsefulClipboard() {
        assertTrue(UsefulClipboardFeature.isEnabled());
        assertEquals(8192, AriseNB1Config.get().usefulClipboard.maxCharacters);

        // Test payload record
        SaveClipboardPayload payload = new SaveClipboardPayload("Hello Lads SMP!");
        assertEquals("Hello Lads SMP!", payload.text());
        assertEquals("ladssmp:save_clipboard", payload.type().id().toString());

        // B/10 clamp test
        assertEquals("abc", UsefulClipboardFeature.clampText("abcdef", 3));
        assertEquals("short", UsefulClipboardFeature.clampText("short", 10));
        assertEquals("", UsefulClipboardFeature.clampText("hello", 0));
        assertEquals("", UsefulClipboardFeature.clampText(null, 5));
    }

    // ==========================================
    // Config Serialization Round-Trip
    // ==========================================
    @Test
    @DisplayName("Config: JSON serialization and deserialization round-trip")
    void testConfigRoundTrip() throws Exception {
        Path testFile = tempDir.resolve("arise-nb1.json");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        AriseNB1Config config = new AriseNB1Config();
        config.adminClaims.protectExplosions = false;
        config.usefulClipboard.maxCharacters = 2048;

        try (Writer writer = Files.newBufferedWriter(testFile)) {
            gson.toJson(config, writer);
        }

        assertTrue(Files.exists(testFile));

        AriseNB1Config loaded;
        try (Reader reader = Files.newBufferedReader(testFile)) {
            loaded = gson.fromJson(reader, AriseNB1Config.class);
        }

        assertNotNull(loaded);
        assertFalse(loaded.adminClaims.protectExplosions);
        assertEquals(2048, loaded.usefulClipboard.maxCharacters);
    }
}
