package com.thelads.smp.features.arise.nb1;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Native clean-room implementation of Custom Item Despawn Duration.
 * Overrides dropped item lifespan globally or per item registry ID.
 */
public final class CustomItemDespawnFeature {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP-ItemDespawn");

    public static void init() {
        LOGGER.info("[Lads SMP] Custom Item Despawn Duration initialized (modifyDefault={}, defaultDuration={}s, overrides={})",
                AriseNB1Config.get().customItemDespawn.modifyDefaultItemDuration,
                AriseNB1Config.get().customItemDespawn.defaultItemDuration,
                AriseNB1Config.get().customItemDespawn.despawnDurations.size());
    }

    public static boolean isEnabled() {
        return AriseNB1Config.get().customItemDespawn.enabled;
    }

    public static boolean shouldDespawn(ItemEntity itemEntity) {
        if (!isEnabled()) return false;

        ItemStack stack = itemEntity.getItem();
        if (stack.isEmpty()) return false;

        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        AriseNB1Config.CustomItemDespawnConfig cfg = AriseNB1Config.get().customItemDespawn;

        if (cfg.despawnDurations.containsKey(itemId)) {
            int targetTicks = cfg.despawnDurations.get(itemId) * 20;
            return itemEntity.getAge() >= targetTicks;
        }

        if (cfg.modifyDefaultItemDuration) {
            int targetTicks = cfg.defaultItemDuration * 20;
            return itemEntity.getAge() >= targetTicks;
        }

        return false;
    }
}
