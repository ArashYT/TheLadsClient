package com.thelads.smp.features.arise.nb7;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main feature registrar for Arise NB7 (Biome Creeper Variants).
 */
public final class AriseNB7Features {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP-NB7");
    private static boolean INITIALIZED = false;

    public static void init() {
        if (INITIALIZED) return;
        INITIALIZED = true;

        AriseNB7Config.load();
        LOGGER.info("[LadsSMP-NB7] Registering 15 Creeper Overhaul biome variants...");

        for (BiomeCreeperVariant variant : BiomeCreeperVariant.values()) {
            String entityId = variant.getId() + "_creeper";
            Identifier entityIdentifier = Identifier.fromNamespaceAndPath("ladssmp", entityId);
            ResourceKey<EntityType<?>> entityKey = ResourceKey.create(Registries.ENTITY_TYPE, entityIdentifier);

            EntityType<BiomeCreeperEntity> entityType = EntityType.Builder.<BiomeCreeperEntity>of(
                    (type, level) -> new BiomeCreeperEntity(type, level, variant),
                    MobCategory.MONSTER
            )
            .sized(0.6F, 1.7F)
            .clientTrackingRange(8)
            .build(entityKey);

            Registry.register(BuiltInRegistries.ENTITY_TYPE, entityKey, entityType);
            variant.setEntityType(entityType);

            // Register default entity attributes (with variant specialization)
            FabricDefaultAttributeRegistry.register(entityType, BiomeCreeperEntity.createAttributes(variant));

            // Register custom spawn egg item
            String eggId = entityId + "_spawn_egg";
            Identifier eggIdentifier = Identifier.fromNamespaceAndPath("ladssmp", eggId);
            ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, eggIdentifier);

            SpawnEggItem eggItem = new SpawnEggItem(
                    new Item.Properties().spawnEgg(entityType).setId(itemKey)
            );
            Registry.register(BuiltInRegistries.ITEM, itemKey, eggItem);
            variant.setSpawnEggItem(eggItem);
        }

        // Register spawn eggs into creative inventory SPAWN_EGGS tab
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(output -> {
            for (BiomeCreeperVariant variant : BiomeCreeperVariant.values()) {
                if (variant.getSpawnEggItem() != null) {
                    output.accept(variant.getSpawnEggItem());
                }
            }
        });

        // Register natural ocean creeper spawning in aquatic/ocean biomes (vanilla creepers cannot spawn in water)
        BiomeModifications.addSpawn(
                BiomeSelectors.tag(BiomeTags.IS_OCEAN),
                MobCategory.MONSTER,
                BiomeCreeperVariant.OCEAN.getEntityType(),
                10,
                1,
                2
        );

        LOGGER.info("[LadsSMP-NB7] Successfully registered 15 biome creeper variants, attributes, and spawn eggs.");
    }
}
