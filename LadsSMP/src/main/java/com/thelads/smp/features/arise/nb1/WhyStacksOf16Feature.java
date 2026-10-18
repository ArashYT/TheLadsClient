package com.thelads.smp.features.arise.nb1;

import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Native clean-room implementation of Why stacks of 16?
 * Increases max stack size to 64 for snowballs, eggs, ender pearls, honey bottles, buckets, and signs.
 */
public final class WhyStacksOf16Feature {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP-WhyStacksOf16");

    public static void init() {
        DefaultItemComponentEvents.MODIFY.register(context -> {
            if (!isEnabled()) return;

            int targetStack = AriseNB1Config.get().whyStacksOf16.stackSize;
            for (String rawId : AriseNB1Config.get().whyStacksOf16.items) {
                Identifier id = Identifier.tryParse(rawId);
                if (id == null) continue;

                Item item = BuiltInRegistries.ITEM.getValue(id);
                if (item != null && item != Items.AIR) {
                    context.modify(item, builder -> builder.set(DataComponents.MAX_STACK_SIZE, targetStack));
                }
            }
        });
        LOGGER.info("[Lads SMP] Why stacks of 16? initialized (maxStackSize={})", AriseNB1Config.get().whyStacksOf16.stackSize);
    }

    public static boolean isEnabled() {
        return AriseNB1Config.get().whyStacksOf16.enabled;
    }
}
