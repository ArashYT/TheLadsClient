package com.thelads.core.features.alwayson.letmedespawn;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.server.level.ServerLevel;

import java.io.File;

public class LetMeDespawn implements ModInitializer {
    public static final String MOD_ID = "letmedespawn";
    public static final Logger logger = LoggerFactory.getLogger(MOD_ID);
    public static final File CONFIG_FILE = new File("config/letmedespawn.json");
    public static LetMeDespawnConfig config;
    private static volatile Registry<Item> cachedItemRegistry;
    private static volatile Registry<net.minecraft.world.entity.EntityType<?>> cachedMobRegistry;

    @Override
    public void onInitialize() {
        config = LetMeDespawnConfig.load();
        config.save();
    }

    public static Registry<Item> getItemRegistry(Level level) {
        Registry<Item> registry = cachedItemRegistry;
        if (registry != null || level == null) return registry;
        synchronized (LetMeDespawn.class) {
            registry = cachedItemRegistry;
            if (registry != null) return registry;
            cachedItemRegistry = registry = level.registryAccess().lookupOrThrow(Registries.ITEM);
            return registry;
        }
    }

    public static String getItemKey(ItemStack itemStack, Mob entity) {
        if (itemStack.isEmpty()) {
            return "";
        }
        Registry<Item> itemRegistry = LetMeDespawn.getItemRegistry(entity.level());
        if (itemRegistry == null) {
            return "";
        }
        Identifier itemId = itemRegistry.getKey(itemStack.getItem());
        return itemId != null ? itemId.toString() : "";
    }

    public static Registry<net.minecraft.world.entity.EntityType<?>> getMobRegistry(Level level) {
        Registry<net.minecraft.world.entity.EntityType<?>> registry = cachedMobRegistry;
        if (registry != null || level == null) return registry;
        synchronized (LetMeDespawn.class) {
            registry = cachedMobRegistry;
            if (registry != null) return registry;
            cachedMobRegistry = registry = level.registryAccess().lookupOrThrow(Registries.ENTITY_TYPE);
            return registry;
        }
    }

    public static String getMobKey(Mob entity) {
        Registry<net.minecraft.world.entity.EntityType<?>> mobRegistry = LetMeDespawn.getMobRegistry(entity.level());
        if (mobRegistry == null) {
            return "";
        }
        Identifier mobId = mobRegistry.getKey(entity.getType());
        return mobId != null ? mobId.toString() : "";
    }

    public static void clearRegistryCache() {
        cachedItemRegistry = null;
        cachedMobRegistry = null;
    }

    public static void setPersistence(Mob entity, EquipmentSlot slot) {
        ItemStack itemStack = entity.getItemBySlot(slot);
        if (!itemStack.isEmpty()) {
            ((LadsEquipmentTracker) entity).lads$markSlotAsPicked(slot, true);
            String mobKey = LetMeDespawn.getMobKey(entity);
            ((LadsEquipmentTracker) entity).lads$setPersistenceRequired(config.getMobNames().contains(mobKey) || !LetMeDespawn.hasDespawnableName(entity));
        }
    }

    public static boolean hasDespawnableName(Mob entity) {
        return !entity.hasCustomName();
    }

    public static void dropEquipmentOnDiscard(Mob mob) {
        if (mob == null || mob.level() == null) return;
        LadsEquipmentTracker tracker = (LadsEquipmentTracker) mob;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (tracker.lads$isSlotPicked(slot)) {
                ItemStack stack = mob.getItemBySlot(slot);
                if (!stack.isEmpty()) {
                    mob.spawnAtLocation((ServerLevel) mob.level(), stack);
                    mob.setItemSlot(slot, ItemStack.EMPTY);
                    tracker.lads$markSlotAsPicked(slot, false);
                }
            }
        }
    }
}
