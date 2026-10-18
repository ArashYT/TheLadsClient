package com.thelads.smp.features.arise.nb10;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Main feature registrar for Arise NB10 (Fairy Lights Hanging Decorations).
 * Registers fastener block, block entity, light strings, bunting, garlands, and creative tab entries.
 */
public final class AriseNB10Features {

    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP-NB10");
    private static boolean INITIALIZED = false;

    public static Block FASTENER_BLOCK;
    public static BlockEntityType<FastenerBlockEntity> FASTENER_BLOCK_ENTITY_TYPE;

    public static Item FASTENER_ITEM;
    public static HangingLightsItem HANGING_LIGHTS_ITEM;
    public static HangingLightsItem HANGING_LIGHTS_RAINBOW_ITEM;
    public static final Map<DyeColor, HangingLightsItem> DYED_LIGHTS_ITEMS = new EnumMap<>(DyeColor.class);
    public static PennantBuntingItem PENNANT_BUNTING_ITEM;
    public static GarlandItem GARLAND_ITEM;
    public static GarlandItem TINSEL_GARLAND_ITEM;

    public static void init() {
        if (INITIALIZED) return;
        INITIALIZED = true;

        AriseNB10Config.load();
        LOGGER.info("[LadsSMP-NB10] Initializing Fairy Lights decorations...");

        // 1. Register Fastener Block & Block Entity
        ResourceKey<Block> fastenerBlockKey = ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("ladssmp", "fastener"));
        FASTENER_BLOCK = Registry.register(BuiltInRegistries.BLOCK, fastenerBlockKey,
                new FastenerBlock(BlockBehaviour.Properties.of()
                        .setId(fastenerBlockKey)
                        .strength(0.5F)
                        .sound(SoundType.CHAIN)
                        .noOcclusion()
                        .pushReaction(PushReaction.POPPED)
                        .lightLevel(state -> state.getValue(FastenerBlock.HAS_LIGHTS) ? AriseNB10Config.get().bulbLightEmission : 0)));

        ResourceKey<BlockEntityType<?>> beKey = ResourceKey.create(Registries.BLOCK_ENTITY_TYPE, Identifier.fromNamespaceAndPath("ladssmp", "fastener"));
        FASTENER_BLOCK_ENTITY_TYPE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, beKey,
                new BlockEntityType<>(FastenerBlockEntity::new, Set.of(FASTENER_BLOCK)));

        // 2. Register Fastener Item
        ResourceKey<Item> fastenerItemKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("ladssmp", "fastener"));
        FASTENER_ITEM = Registry.register(BuiltInRegistries.ITEM, fastenerItemKey,
                new BlockItem(FASTENER_BLOCK, new Item.Properties().setId(fastenerItemKey)));

        // 3. Register Hanging Light Strings (Warm White & Rainbow)
        ResourceKey<Item> warmLightsKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("ladssmp", "hanging_lights"));
        HANGING_LIGHTS_ITEM = Registry.register(BuiltInRegistries.ITEM, warmLightsKey,
                new HangingLightsItem(new Item.Properties().setId(warmLightsKey), HangingLightsItem.WARM_WHITE));

        ResourceKey<Item> rainbowLightsKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("ladssmp", "hanging_lights_rainbow"));
        HANGING_LIGHTS_RAINBOW_ITEM = Registry.register(BuiltInRegistries.ITEM, rainbowLightsKey,
                new HangingLightsItem(new Item.Properties().setId(rainbowLightsKey), HangingLightsItem.RAINBOW));

        // 4. Register 16 Dyed Light Variants
        for (DyeColor color : DyeColor.values()) {
            String id = "hanging_lights_" + color.getName();
            ResourceKey<Item> dyedKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("ladssmp", id));
            HangingLightsItem dyedItem = Registry.register(BuiltInRegistries.ITEM, dyedKey,
                    HangingLightsItem.createDyed(new Item.Properties().setId(dyedKey), color));
            DYED_LIGHTS_ITEMS.put(color, dyedItem);
        }

        // 5. Register Pennant Bunting & Garlands
        ResourceKey<Item> buntingKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("ladssmp", "pennant_bunting"));
        PENNANT_BUNTING_ITEM = Registry.register(BuiltInRegistries.ITEM, buntingKey,
                new PennantBuntingItem(new Item.Properties().setId(buntingKey)));

        ResourceKey<Item> garlandKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("ladssmp", "garland"));
        GARLAND_ITEM = Registry.register(BuiltInRegistries.ITEM, garlandKey,
                new GarlandItem(new Item.Properties().setId(garlandKey), false));

        ResourceKey<Item> tinselKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("ladssmp", "tinsel_garland"));
        TINSEL_GARLAND_ITEM = Registry.register(BuiltInRegistries.ITEM, tinselKey,
                new GarlandItem(new Item.Properties().setId(tinselKey), true));

        // 6. Register into Creative Mode Tabs (Functional Blocks)
        ResourceKey<CreativeModeTab> tabKey = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath("minecraft", "functional_blocks"));
        CreativeModeTabEvents.modifyOutputEvent(tabKey).register(output -> {
            output.accept(FASTENER_ITEM);
            output.accept(HANGING_LIGHTS_ITEM);
            output.accept(HANGING_LIGHTS_RAINBOW_ITEM);
            for (HangingLightsItem dyed : DYED_LIGHTS_ITEMS.values()) {
                output.accept(dyed);
            }
            output.accept(PENNANT_BUNTING_ITEM);
            output.accept(GARLAND_ITEM);
            output.accept(TINSEL_GARLAND_ITEM);
        });

        // 7. Initialize Connection Tracker
        ConnectionTracker.init();

        LOGGER.info("[LadsSMP-NB10] Successfully registered Fairy Lights decorations, fastener blocks, and items.");
    }
}
