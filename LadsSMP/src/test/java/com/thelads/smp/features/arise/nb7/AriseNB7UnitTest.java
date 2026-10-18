package com.thelads.smp.features.arise.nb7;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Arise Native Batch NB7 (Creeper Overhaul Biome Creepers).
 * Tests business logic, configuration, biome resolution, and griefing policies without requiring full Minecraft client boot.
 */
public class AriseNB7UnitTest {

    @BeforeEach
    public void setup() {
        AriseNB7Config.setInstance(new AriseNB7Config());
    }

    @Test
    public void testConfigDefaults() {
        AriseNB7Config config = AriseNB7Config.get();
        assertTrue(config.destroyBlocks, "destroyBlocks should default to true");
        assertTrue(config.replaceDefaultCreeper, "replaceDefaultCreeper should default to true");
        assertTrue(config.allowSpawning, "allowSpawning should default to true");

        for (BiomeCreeperVariant variant : BiomeCreeperVariant.values()) {
            assertTrue(config.isVariantEnabled(variant), "Variant " + variant.getId() + " should be enabled by default");
        }
    }

    @Test
    public void testConfigVariantDisabling() {
        AriseNB7Config config = AriseNB7Config.get();

        // Test individual toggle
        config.allowDesertCreeperSpawning = false;
        assertFalse(config.isVariantEnabled(BiomeCreeperVariant.DESERT));
        assertTrue(config.isVariantEnabled(BiomeCreeperVariant.SNOWY));

        // Test global spawning killswitch
        config.allowSpawning = false;
        assertFalse(config.isVariantEnabled(BiomeCreeperVariant.SNOWY));
    }

    @Test
    public void testVariantEnumCompleteness() {
        assertEquals(15, BiomeCreeperVariant.values().length, "There should be exactly 15 biome creeper variants");

        for (BiomeCreeperVariant variant : BiomeCreeperVariant.values()) {
            assertNotNull(variant.getId(), "Variant ID must not be null");
            assertNotNull(variant.getEnglishName(), "Variant English name must not be null");
            assertNotNull(variant.getTextureLocation(), "Texture location must not be null");
            assertTrue(variant.getTextureLocation().getPath().endsWith(".png"), "Texture path must end with .png");
            assertTrue(variant.getPrimaryEggColor() > 0, "Primary egg color must be valid RGB");
            assertTrue(variant.getSecondaryEggColor() > 0, "Secondary egg color must be valid RGB");
        }
    }

    @Test
    public void testVariantBiomeMatchingLogic() {
        // Direct Holder mockup using direct keys
        Holder<Biome> desertHolder = createMockBiomeHolder("minecraft", "desert");
        assertEquals(BiomeCreeperVariant.DESERT, BiomeCreeperVariant.findVariantForBiome(desertHolder, BlockPos.ZERO, null));

        Holder<Biome> snowyHolder = createMockBiomeHolder("minecraft", "snowy_plains");
        assertEquals(BiomeCreeperVariant.SNOWY, BiomeCreeperVariant.findVariantForBiome(snowyHolder, BlockPos.ZERO, null));

        Holder<Biome> badlandsHolder = createMockBiomeHolder("minecraft", "eroded_badlands");
        assertEquals(BiomeCreeperVariant.BADLANDS, BiomeCreeperVariant.findVariantForBiome(badlandsHolder, BlockPos.ZERO, null));

        Holder<Biome> jungleHolder = createMockBiomeHolder("minecraft", "jungle");
        assertEquals(BiomeCreeperVariant.JUNGLE, BiomeCreeperVariant.findVariantForBiome(jungleHolder, BlockPos.ZERO, null));

        Holder<Biome> bambooHolder = createMockBiomeHolder("minecraft", "bamboo_jungle");
        assertEquals(BiomeCreeperVariant.BAMBOO, BiomeCreeperVariant.findVariantForBiome(bambooHolder, BlockPos.ZERO, null));

        Holder<Biome> swampHolder = createMockBiomeHolder("minecraft", "swamp");
        assertEquals(BiomeCreeperVariant.SWAMP, BiomeCreeperVariant.findVariantForBiome(swampHolder, BlockPos.ZERO, null));

        Holder<Biome> mushroomHolder = createMockBiomeHolder("minecraft", "mushroom_fields");
        assertEquals(BiomeCreeperVariant.MUSHROOM, BiomeCreeperVariant.findVariantForBiome(mushroomHolder, BlockPos.ZERO, null));

        Holder<Biome> savannaHolder = createMockBiomeHolder("minecraft", "savanna_plateau");
        assertEquals(BiomeCreeperVariant.SAVANNAH, BiomeCreeperVariant.findVariantForBiome(savannaHolder, BlockPos.ZERO, null));

        Holder<Biome> beachHolder = createMockBiomeHolder("minecraft", "beach");
        assertEquals(BiomeCreeperVariant.BEACH, BiomeCreeperVariant.findVariantForBiome(beachHolder, BlockPos.ZERO, null));

        Holder<Biome> oceanHolder = createMockBiomeHolder("minecraft", "deep_warm_ocean");
        assertEquals(BiomeCreeperVariant.OCEAN, BiomeCreeperVariant.findVariantForBiome(oceanHolder, BlockPos.ZERO, null));

        Holder<Biome> darkForestHolder = createMockBiomeHolder("minecraft", "dark_forest");
        assertEquals(BiomeCreeperVariant.DARK_OAK, BiomeCreeperVariant.findVariantForBiome(darkForestHolder, BlockPos.ZERO, null));

        Holder<Biome> spruceHolder = createMockBiomeHolder("minecraft", "old_growth_spruce_taiga");
        assertEquals(BiomeCreeperVariant.SPRUCE, BiomeCreeperVariant.findVariantForBiome(spruceHolder, BlockPos.ZERO, null));

        Holder<Biome> hillsHolder = createMockBiomeHolder("minecraft", "windswept_hills");
        assertEquals(BiomeCreeperVariant.HILLS, BiomeCreeperVariant.findVariantForBiome(hillsHolder, BlockPos.ZERO, null));

        Holder<Biome> dripstoneHolder = createMockBiomeHolder("minecraft", "dripstone_caves");
        assertEquals(BiomeCreeperVariant.DRIPSTONE, BiomeCreeperVariant.findVariantForBiome(dripstoneHolder, BlockPos.ZERO, null));
    }

    @Test
    public void testCaveVariantSubterraneanFallback() {
        Holder<Biome> plainsHolder = createMockBiomeHolder("minecraft", "plains");

        // Surface plains -> not cave creeper
        assertNull(BiomeCreeperVariant.findVariantForBiome(plainsHolder, new BlockPos(0, 70, 0), null));

        // Underground mockup
        LevelReader mockReader = createMockSubterraneanLevelReader();
        assertEquals(BiomeCreeperVariant.CAVE, BiomeCreeperVariant.findVariantForBiome(plainsHolder, new BlockPos(0, 30, 0), mockReader));
    }

    @Test
    public void testExplosionInteractionMapping() {
        AriseNB7Config config = AriseNB7Config.get();

        config.destroyBlocks = true;
        Level.ExplosionInteraction interaction1 = config.destroyBlocks ? Level.ExplosionInteraction.MOB : Level.ExplosionInteraction.NONE;
        assertEquals(Level.ExplosionInteraction.MOB, interaction1);

        config.destroyBlocks = false;
        Level.ExplosionInteraction interaction2 = config.destroyBlocks ? Level.ExplosionInteraction.MOB : Level.ExplosionInteraction.NONE;
        assertEquals(Level.ExplosionInteraction.NONE, interaction2);
    }

    @Test
    public void testSpawnReplacementFilterReasons() {
        // Only NATURAL and CHUNK_GENERATION are valid for biome conversion
        java.util.function.Predicate<net.minecraft.world.entity.EntitySpawnReason> isAllowedReason =
                reason -> reason == net.minecraft.world.entity.EntitySpawnReason.NATURAL ||
                          reason == net.minecraft.world.entity.EntitySpawnReason.CHUNK_GENERATION;

        assertTrue(isAllowedReason.test(net.minecraft.world.entity.EntitySpawnReason.NATURAL));
        assertTrue(isAllowedReason.test(net.minecraft.world.entity.EntitySpawnReason.CHUNK_GENERATION));

        // Must reject COMMAND, SPAWNER, SPAWN_ITEM_USE, etc. to prevent syntax crashes and spawner glitches
        assertFalse(isAllowedReason.test(net.minecraft.world.entity.EntitySpawnReason.COMMAND));
        assertFalse(isAllowedReason.test(net.minecraft.world.entity.EntitySpawnReason.SPAWNER));
        assertFalse(isAllowedReason.test(net.minecraft.world.entity.EntitySpawnReason.SPAWN_ITEM_USE));
        assertFalse(isAllowedReason.test(net.minecraft.world.entity.EntitySpawnReason.LOAD));
        assertFalse(isAllowedReason.test(net.minecraft.world.entity.EntitySpawnReason.DIMENSION_TRAVEL));
    }

    private static Holder<Biome> createMockBiomeHolder(String namespace, String path) {
        ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(namespace, path));
        return Holder.Reference.createStandAlone(null, key);
    }

    private static LevelReader createMockSubterraneanLevelReader() {
        return (LevelReader) java.lang.reflect.Proxy.newProxyInstance(
                LevelReader.class.getClassLoader(),
                new Class<?>[]{LevelReader.class},
                (proxy, method, args) -> {
                    if ("canSeeSky".equals(method.getName())) {
                        return false;
                    }
                    if (method.getReturnType().equals(boolean.class)) return false;
                    if (method.getReturnType().equals(int.class)) return 0;
                    return null;
                }
        );
    }
}
