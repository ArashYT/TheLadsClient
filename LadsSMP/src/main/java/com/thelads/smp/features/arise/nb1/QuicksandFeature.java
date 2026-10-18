package com.thelads.smp.features.arise.nb1;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.material.MapColor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Native clean-room implementation of Enhanced Nature quicksand hazard.
 * Registers the quicksand block and placed worldgen feature in desert and river shore biomes.
 */
public final class QuicksandFeature {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP-Quicksand");

    public static Block QUICKSAND_BLOCK;
    public static Item QUICKSAND_ITEM;
    public static ResourceKey<net.minecraft.world.level.levelgen.placement.PlacedFeature> QUICKSAND_PLACED_KEY;

    private static Block registerBlock(String name, Block block) {
        return Registry.register(BuiltInRegistries.BLOCK, Identifier.fromNamespaceAndPath("ladssmp", name), block);
    }

    private static Item registerItem(String name, Item item) {
        return Registry.register(BuiltInRegistries.ITEM, Identifier.fromNamespaceAndPath("ladssmp", name), item);
    }

    public static void init() {
        if (QUICKSAND_BLOCK == null) {
            QUICKSAND_BLOCK = registerBlock("quicksand",
                    new QuicksandBlock(BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("ladssmp", "quicksand")))
                            .mapColor(MapColor.SAND)
                            .strength(0.5F)
                            .sound(SoundType.SAND)
                            .noOcclusion()));
            QUICKSAND_ITEM = registerItem("quicksand",
                    new BlockItem(QUICKSAND_BLOCK, new Item.Properties().setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("ladssmp", "quicksand"))).useBlockDescriptionPrefix()));
            QUICKSAND_PLACED_KEY = ResourceKey.create(Registries.PLACED_FEATURE, Identifier.fromNamespaceAndPath("ladssmp", "quicksand_patch"));
        }

        if (isEnabled() && AriseNB1Config.get().quicksand.generateInWorld) {
            BiomeModifications.addFeature(
                    BiomeSelectors.includeByKey(Biomes.DESERT, Biomes.RIVER),
                    GenerationStep.Decoration.UNDERGROUND_ORES,
                    QUICKSAND_PLACED_KEY
            );
        }
        LOGGER.info("[Lads SMP] Enhanced Nature quicksand registered");
    }

    public static boolean isEnabled() {
        return AriseNB1Config.get().quicksand.enabled;
    }
}
