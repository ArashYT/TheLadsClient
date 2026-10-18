package com.thelads.smp.features.arise.nb9;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Arise Native Batch NB9 (Amendments-Style Vanilla Block Upgrades).
 * Tests business logic, potion mixing, inverse neutralization, color blending,
 * hanging sign item tracking, and configuration defaults.
 */
public class AriseNB9UnitTest {

    @BeforeEach
    public void setup() {
        AriseNB9Config.setInstance(new AriseNB9Config());
    }

    @Test
    public void testConfigDefaults() {
        AriseNB9Config config = AriseNB9Config.get();
        assertTrue(config.enhancedCauldron, "enhancedCauldron should default to true");
        assertTrue(config.dyeWater, "dyeWater should default to true");
        assertEquals(4, config.dyeRecipesPerLayer, "dyeRecipesPerLayer should default to 4");
        assertEquals(2, config.potionRecipesPerLayer, "potionRecipesPerLayer should default to 2");
        assertEquals("ON", config.potionsMixing, "potionsMixing should default to ON");
        assertEquals(8, config.potionMixingLimit, "potionMixingLimit should default to 8");

        assertTrue(config.wallLanterns, "wallLanterns should default to true");
        assertTrue(config.itemsOnSigns, "itemsOnSigns should default to true");
        assertTrue(config.lecternBookTitle, "lecternBookTitle should default to true");
        assertTrue(config.skullPiles, "skullPiles should default to true");
        assertTrue(config.skullCandles, "skullCandles should default to true");
        assertTrue(config.carpetedStairs, "carpetedStairs should default to true");
        assertTrue(config.carpetedSlabs, "carpetedSlabs should default to true");
        assertTrue(config.doubleCake, "doubleCake should default to true");
        assertTrue(config.chainRinging, "chainRinging should default to true");
        assertEquals(16, config.chainLength, "chainLength should default to 16");
        assertTrue(config.toolHook, "toolHook should default to true");
        assertTrue(config.torchFire, "torchFire should default to true");
        assertEquals(2, config.torchFireDurationSeconds, "torchFireDurationSeconds should default to 2");
        assertTrue(config.hangingPot, "hangingPot should default to true");
        assertTrue(config.betterLilypads, "betterLilypads should default to true");
    }

    @Test
    public void testInversePotionsMap() {
        AriseNB9Config config = AriseNB9Config.get();
        Map<String, String> map = config.inversePotions;
        assertNotNull(map);
        assertEquals("minecraft:slowness", map.get("minecraft:speed"));
        assertEquals("minecraft:speed", map.get("minecraft:slowness"));
        assertEquals("minecraft:strength", map.get("minecraft:weakness"));
        assertEquals("minecraft:weakness", map.get("minecraft:strength"));
        assertEquals("minecraft:instant_damage", map.get("minecraft:instant_health"));
        assertEquals("minecraft:instant_health", map.get("minecraft:instant_damage"));
        assertEquals("minecraft:haste", map.get("minecraft:mining_fatigue"));
        assertEquals("minecraft:mining_fatigue", map.get("minecraft:haste"));
        assertEquals("minecraft:luck", map.get("minecraft:unluck"));
        assertEquals("minecraft:unluck", map.get("minecraft:luck"));
    }

    @Test
    public void testPotionMixingCombiningEffects() {
        List<PotionMixingHelper.PotionEffectEntry> existing = List.of(
                new PotionMixingHelper.PotionEffectEntry("minecraft:speed", 3600, 0)
        );
        List<PotionMixingHelper.PotionEffectEntry> incoming = List.of(
                new PotionMixingHelper.PotionEffectEntry("minecraft:jump_boost", 3600, 0)
        );

        PotionMixingHelper.MixedPotionResult result = PotionMixingHelper.mix(
                existing, incoming, AriseNB9Config.get().inversePotions, 8, 0x33EBFF, 0x22FF44
        );

        assertEquals(2, result.effects().size(), "Combined potion should have both effects");
        assertEquals("minecraft:speed", result.effects().get(0).effectId());
        assertEquals("minecraft:jump_boost", result.effects().get(1).effectId());
    }

    @Test
    public void testPotionMixingInverseNeutralization() {
        // Cauldron already has Speed I
        List<PotionMixingHelper.PotionEffectEntry> existing = List.of(
                new PotionMixingHelper.PotionEffectEntry("minecraft:speed", 3600, 0)
        );
        // Player pours in Slowness I (exact inverse of Speed)
        List<PotionMixingHelper.PotionEffectEntry> incoming = List.of(
                new PotionMixingHelper.PotionEffectEntry("minecraft:slowness", 1800, 0)
        );

        PotionMixingHelper.MixedPotionResult result = PotionMixingHelper.mix(
                existing, incoming, AriseNB9Config.get().inversePotions, 8, 0x33EBFF, 0x5A6C82
        );

        assertTrue(result.effects().isEmpty(), "Speed and Slowness of equal amplifier should neutralize each other");
    }

    @Test
    public void testPotionMixingLimitEnforced() {
        List<PotionMixingHelper.PotionEffectEntry> existing = List.of(
                new PotionMixingHelper.PotionEffectEntry("effect1", 100, 0),
                new PotionMixingHelper.PotionEffectEntry("effect2", 100, 0),
                new PotionMixingHelper.PotionEffectEntry("effect3", 100, 0),
                new PotionMixingHelper.PotionEffectEntry("effect4", 100, 0),
                new PotionMixingHelper.PotionEffectEntry("effect5", 100, 0),
                new PotionMixingHelper.PotionEffectEntry("effect6", 100, 0),
                new PotionMixingHelper.PotionEffectEntry("effect7", 100, 0),
                new PotionMixingHelper.PotionEffectEntry("effect8", 100, 0)
        );
        List<PotionMixingHelper.PotionEffectEntry> incoming = List.of(
                new PotionMixingHelper.PotionEffectEntry("effect9", 100, 0),
                new PotionMixingHelper.PotionEffectEntry("effect10", 100, 0)
        );

        PotionMixingHelper.MixedPotionResult result = PotionMixingHelper.mix(
                existing, incoming, AriseNB9Config.get().inversePotions, 8, 0x111111, 0x222222
        );

        assertEquals(8, result.effects().size(), "Potion mixing limit must cap effect list at 8");
    }

    @Test
    public void testColorBlending() {
        int red = 0xFF0000;
        int blue = 0x0000FF;
        int blended = PotionMixingHelper.blendColors(red, blue);

        int r = (blended >> 16) & 0xFF;
        int g = (blended >> 8) & 0xFF;
        int b = blended & 0xFF;

        assertEquals(0x7F, r, "Red component should be averaged");
        assertEquals(0x00, g, "Green component should remain zero");
        assertEquals(0x7F, b, "Blue component should be averaged");
    }

    @Test
    public void testHangingSignItemManagerTracking() {
        BlockPos pos = new BlockPos(10, 64, -20);
        assertTrue(HangingSignItemManager.getDisplayedItem(pos).isEmpty());

        HangingSignItemManager.setDisplayedItem(pos, null);
        assertTrue(HangingSignItemManager.getDisplayedItem(pos).isEmpty());

        HangingSignItemManager.setDisplayedItem(pos, ItemStack.EMPTY);
        assertTrue(HangingSignItemManager.getDisplayedItem(pos).isEmpty());
    }
}
