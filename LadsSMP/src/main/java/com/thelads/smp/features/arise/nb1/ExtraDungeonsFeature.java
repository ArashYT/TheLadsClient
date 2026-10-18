package com.thelads.smp.features.arise.nb1;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Native clean-room implementation of Extra Dungeons.
 * Adds 6 themed variants of vanilla underground monster rooms:
 * Drowned, Husk, Skeleton, Spider, Stray, and Zombie dungeons with biome-adaptive palettes.
 */
public final class ExtraDungeonsFeature {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP-ExtraDungeons");

    public enum DungeonTheme {
        DROWNED(() -> EntityTypes.DROWNED, () -> Blocks.PRISMARINE_BRICKS.defaultBlockState(), () -> Blocks.MOSSY_COBBLESTONE.defaultBlockState()),
        HUSK(() -> EntityTypes.HUSK, () -> Blocks.CUT_SANDSTONE.defaultBlockState(), () -> Blocks.SANDSTONE.defaultBlockState()),
        SKELETON(() -> EntityTypes.SKELETON, () -> Blocks.COBBLED_DEEPSLATE.defaultBlockState(), () -> Blocks.POLISHED_DEEPSLATE.defaultBlockState()),
        SPIDER(() -> EntityTypes.SPIDER, () -> Blocks.COBBLESTONE.defaultBlockState(), () -> Blocks.MOSSY_COBBLESTONE.defaultBlockState()),
        STRAY(() -> EntityTypes.STRAY, () -> Blocks.PACKED_ICE.defaultBlockState(), () -> Blocks.SNOW_BLOCK.defaultBlockState()),
        ZOMBIE(() -> EntityTypes.ZOMBIE, () -> Blocks.MOSSY_COBBLESTONE.defaultBlockState(), () -> Blocks.MUD_BRICKS.defaultBlockState());

        private final java.util.function.Supplier<EntityType<?>> entityTypeSupplier;
        private final java.util.function.Supplier<BlockState> primaryWallSupplier;
        private final java.util.function.Supplier<BlockState> secondaryWallSupplier;

        DungeonTheme(java.util.function.Supplier<EntityType<?>> entityTypeSupplier,
                     java.util.function.Supplier<BlockState> primaryWallSupplier,
                     java.util.function.Supplier<BlockState> secondaryWallSupplier) {
            this.entityTypeSupplier = entityTypeSupplier;
            this.primaryWallSupplier = primaryWallSupplier;
            this.secondaryWallSupplier = secondaryWallSupplier;
        }

        public EntityType<?> getEntityType() {
            return entityTypeSupplier.get();
        }

        public BlockState getPrimaryWall() {
            return primaryWallSupplier.get();
        }

        public BlockState getSecondaryWall() {
            return secondaryWallSupplier.get();
        }
    }

    public static void init() {
        LOGGER.info("[Lads SMP] Extra Dungeons initialized (6 themed variants active)");
    }

    public static boolean isEnabled() {
        return AriseNB1Config.get().extraDungeons.enabled;
    }

    public static DungeonTheme selectThemeForPosition(WorldGenLevel level, BlockPos pos, RandomSource random) {
        if (!isEnabled()) {
            return DungeonTheme.ZOMBIE;
        }

        Holder<Biome> biomeHolder = level.getBiome(pos);

        if (biomeHolder.is(BiomeTags.IS_OCEAN) || biomeHolder.is(BiomeTags.IS_RIVER) || biomeHolder.is(BiomeTags.IS_BEACH)) {
            return DungeonTheme.DROWNED;
        }
        if (biomeHolder.is(BiomeTags.IS_BADLANDS) || biomeHolder.is(BiomeTags.HAS_DESERT_PYRAMID)) {
            return DungeonTheme.HUSK;
        }
        if (biomeHolder.is(BiomeTags.SPAWNS_SNOW_FOXES) || biomeHolder.is(BiomeTags.IS_TAIGA)) {
            return DungeonTheme.STRAY;
        }
        if (pos.getY() < 0) {
            return random.nextBoolean() ? DungeonTheme.SKELETON : DungeonTheme.ZOMBIE;
        }

        // Random roll across all 6 variants
        DungeonTheme[] themes = DungeonTheme.values();
        return themes[random.nextInt(themes.length)];
    }
}
