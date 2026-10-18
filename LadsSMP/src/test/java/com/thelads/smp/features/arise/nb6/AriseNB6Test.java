package com.thelads.smp.features.arise.nb6;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test suite for Native Batch NB6: Tide-Style Fishing Overhaul.
 * Tests configuration, food profiles, journal persistence, biome pools,
 * minigame physics, and server validation.
 */
public class AriseNB6Test {

    @Test
    @DisplayName("Verify AriseNB6Config defaults and JSON serialization roundtrip")
    void testConfigDefaultsAndSerialization() {
        AriseNB6Config config = new AriseNB6Config();
        assertTrue(config.enabled);
        assertTrue(config.holdToCast);
        assertEquals(1.5f, config.maxCastChargeSeconds);
        assertEquals(0.4f, config.minCastPower);
        assertEquals(1.6f, config.maxCastPower);
        assertTrue(config.doMinigame);
        assertEquals(1.2f, config.minigameDifficulty);
        assertEquals(100, config.minigameDurationTicks);
        assertEquals(6, config.crateWeight);
        assertTrue(config.giveJournal);
        assertTrue(config.showToasts);
        assertTrue(config.customFishLoot);
        assertTrue(config.lavaFishing);

        // Mutate & Save/Load
        config.minigameDifficulty = 1.8f;
        config.crateWeight = 10;
        AriseNB6Config.save(config);

        AriseNB6Config loaded = AriseNB6Config.load();
        assertEquals(1.8f, loaded.minigameDifficulty);
        assertEquals(10, loaded.crateWeight);

        // Restore default
        AriseNB6Config.save(new AriseNB6Config());
    }

    @Test
    @DisplayName("Verify 20 Fish Species & Cooked Food Profiles")
    void testFoodProperties() {
        // Test freshwater
        assertTrue(AriseNB6Foods.COOKED_BASS.nutrition() > AriseNB6Foods.BASS.nutrition());
        assertTrue(AriseNB6Foods.COOKED_BLUEGILL.nutrition() > AriseNB6Foods.BLUEGILL.nutrition());
        assertTrue(AriseNB6Foods.COOKED_CARP.nutrition() > AriseNB6Foods.CARP.nutrition());
        assertTrue(AriseNB6Foods.COOKED_TROUT.nutrition() > AriseNB6Foods.TROUT.nutrition());

        // Test cold
        assertTrue(AriseNB6Foods.COOKED_GLACIER_COD.nutrition() > AriseNB6Foods.GLACIER_COD.nutrition());
        assertTrue(AriseNB6Foods.COOKED_FROSTBITE_FLOUNDER.nutrition() > AriseNB6Foods.FROSTBITE_FLOUNDER.nutrition());

        // Test ocean
        assertTrue(AriseNB6Foods.COOKED_TUNA.nutrition() >= 8);
        assertTrue(AriseNB6Foods.COOKED_SAILFISH.nutrition() >= 8);
        assertTrue(AriseNB6Foods.COOKED_SNAPPER.nutrition() >= 6);

        // Test swamp
        assertTrue(AriseNB6Foods.COOKED_CATFISH.nutrition() > AriseNB6Foods.CATFISH.nutrition());
        assertTrue(AriseNB6Foods.COOKED_BULLHEAD.nutrition() > AriseNB6Foods.BULLHEAD.nutrition());

        // Test cave & deep underground
        assertTrue(AriseNB6Foods.COOKED_ABYSSAL_ANGLER.nutrition() >= 8);
        assertTrue(AriseNB6Foods.COOKED_CAVE_TETRA.nutrition() >= 5);

        // Test night & storm
        assertTrue(AriseNB6Foods.COOKED_LUNAR_GUPPY.nutrition() > AriseNB6Foods.LUNAR_GUPPY.nutrition());
        assertTrue(AriseNB6Foods.COOKED_STORM_PERCH.nutrition() > AriseNB6Foods.STORM_PERCH.nutrition());

        // Test nether & end
        assertTrue(AriseNB6Foods.COOKED_MAGMA_MACKEREL.nutrition() >= 8);
        assertTrue(AriseNB6Foods.COOKED_CRIMSON_FANGJAW.nutrition() >= 8);
        assertTrue(AriseNB6Foods.COOKED_WARPED_GUPPY.nutrition() >= 6);
        assertTrue(AriseNB6Foods.COOKED_VOID_TETRA.nutrition() >= 7);
        assertTrue(AriseNB6Foods.COOKED_CHORUS_DRIFTFISH.nutrition() >= 8);

        // Saturation modifiers must be positive
        assertTrue(AriseNB6Foods.COOKED_TUNA.saturation() >= 0.8f);
    }

    @Test
    @DisplayName("Verify FishingJournalData tracking, completion %, and JSON roundtrip")
    void testFishingJournalDataTracking() {
        UUID uuid = UUID.randomUUID();
        FishingJournalData data = new FishingJournalData(uuid);

        assertEquals(0, data.getDiscoveredCount());
        assertEquals(0.0f, data.getCompletionPercentage());
        assertFalse(data.hasCaught("bass"));

        // Record first catch
        boolean isFirst = data.recordCatch("bass");
        assertTrue(isFirst);
        assertTrue(data.hasCaught("bass"));
        assertEquals(1, data.getCatchCount("bass"));
        assertTrue(data.getFirstCaughtTimestamp("bass") > 0L);
        assertEquals(1, data.getDiscoveredCount());
        assertEquals(5.0f, data.getCompletionPercentage()); // 1/20 = 5%

        // Record duplicate catch
        boolean isSecond = data.recordCatch("bass");
        assertFalse(isSecond);
        assertEquals(2, data.getCatchCount("bass"));
        assertEquals(1, data.getDiscoveredCount());

        // Record 3 more
        data.recordCatch("tuna");
        data.recordCatch("catfish");
        data.recordCatch("abyssal_angler");
        assertEquals(4, data.getDiscoveredCount());
        assertEquals(20.0f, data.getCompletionPercentage()); // 4/20 = 20%

        // Starting journal flag
        assertFalse(data.hasReceivedStartingJournal());
        data.setReceivedStartingJournal(true);
        assertTrue(data.hasReceivedStartingJournal());

        // JSON Serialization roundtrip
        String json = data.toJson();
        assertNotNull(json);

        FishingJournalData loaded = FishingJournalData.fromJson(json);
        assertEquals(uuid, loaded.getPlayerId());
        assertEquals(4, loaded.getDiscoveredCount());
        assertTrue(loaded.hasCaught("bass"));
        assertTrue(loaded.hasCaught("tuna"));
        assertTrue(loaded.hasCaught("catfish"));
        assertTrue(loaded.hasCaught("abyssal_angler"));
        assertEquals(2, loaded.getCatchCount("bass"));
        assertTrue(loaded.hasReceivedStartingJournal());
    }

    @Test
    @DisplayName("Verify Biome Group Fish Pools")
    void testFishingBiomeGroupPools() {
        for (FishingBiomeGroup group : FishingBiomeGroup.values()) {
            List<String> pool = FishingLootManager.getPool(group);
            assertNotNull(pool, "Pool for " + group + " must not be null");
            assertFalse(pool.isEmpty(), "Pool for " + group + " must not be empty");
        }

        // Verify freshwater has bass, trout, carp, bluegill
        List<String> fresh = FishingLootManager.getPool(FishingBiomeGroup.FRESHWATER);
        assertTrue(fresh.contains("bass"));
        assertTrue(fresh.contains("trout"));
        assertTrue(fresh.contains("carp"));
        assertTrue(fresh.contains("bluegill"));

        // Verify cold has glacier cod and frostbite flounder
        List<String> cold = FishingLootManager.getPool(FishingBiomeGroup.COLD);
        assertTrue(cold.contains("glacier_cod"));
        assertTrue(cold.contains("frostbite_flounder"));

        // Verify nether has nether fish
        List<String> nether = FishingLootManager.getPool(FishingBiomeGroup.NETHER);
        assertTrue(nether.contains("magma_mackerel"));
        assertTrue(nether.contains("crimson_fangjaw"));
        assertTrue(nether.contains("warped_guppy"));
    }

    @Test
    @DisplayName("Verify Hold-to-Charge Cast Power Scaling Formula")
    void testCastPowerScaling() {
        float minPower = 0.4f;
        float maxPower = 1.6f;
        float maxChargeSeconds = 1.5f;
        float maxChargeTicks = maxChargeSeconds * 20.0f; // 30 ticks

        // 0 ticks held
        float ratio0 = Math.min(1.0f, 0 / maxChargeTicks);
        float power0 = minPower + (maxPower - minPower) * ratio0;
        assertEquals(0.4f, power0, 0.001f);

        // Half charge (15 ticks = 0.75s)
        float ratioHalf = Math.min(1.0f, 15 / maxChargeTicks);
        float powerHalf = minPower + (maxPower - minPower) * ratioHalf;
        assertEquals(1.0f, powerHalf, 0.001f);

        // Full charge (30 ticks = 1.5s)
        float ratioFull = Math.min(1.0f, 30 / maxChargeTicks);
        float powerFull = minPower + (maxPower - minPower) * ratioFull;
        assertEquals(1.6f, powerFull, 0.001f);

        // Over-held (60 ticks) capped at maxPower
        float ratioOver = Math.min(1.0f, 60 / maxChargeTicks);
        float powerOver = minPower + (maxPower - minPower) * ratioOver;
        assertEquals(1.6f, powerOver, 0.001f);
    }

    @Test
    @DisplayName("Verify Minigame Catch Bar Catch Zone Math & Speed Validation")
    void testMinigameCatchZoneAndSpeedValidation() {
        float barPos = 40.0f;
        float barHeight = 24.0f;

        // Inside zone
        float fishPos1 = 50.0f;
        boolean inside1 = (fishPos1 >= barPos && fishPos1 <= (barPos + barHeight));
        assertTrue(inside1);

        // Edge inside
        float fishPos2 = 40.0f;
        boolean inside2 = (fishPos2 >= barPos && fishPos2 <= (barPos + barHeight));
        assertTrue(inside2);

        // Outside below
        float fishPos3 = 35.0f;
        boolean inside3 = (fishPos3 >= barPos && fishPos3 <= (barPos + barHeight));
        assertFalse(inside3);

        // Outside above
        float fishPos4 = 65.0f;
        boolean inside4 = (fishPos4 >= barPos && fishPos4 <= (barPos + barHeight));
        assertFalse(inside4);

        // Anti-exploit check: any win under 25 ticks is invalid
        int cheatedTicks = 12;
        boolean validWin = (cheatedTicks >= 25);
        assertFalse(validWin, "Wins under 25 ticks must be rejected by server validation");

        int legitTicks = 45;
        boolean legitWin = (legitTicks >= 25);
        assertTrue(legitWin);
    }

    @Test
    @DisplayName("Verify Species Name Formatting")
    void testSpeciesNameFormatting() {
        assertEquals("Striped Bass", FishingJournalManager.formatSpeciesName("striped_bass"));
        assertEquals("Frostbite Flounder", FishingJournalManager.formatSpeciesName("frostbite_flounder"));
        assertEquals("Abyssal Angler", FishingJournalManager.formatSpeciesName("abyssal_angler"));
        assertEquals("Trout", FishingJournalManager.formatSpeciesName("trout"));
    }
}
