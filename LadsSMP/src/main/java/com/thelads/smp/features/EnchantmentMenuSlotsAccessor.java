package com.thelads.smp.features;

import net.minecraft.world.Container;

/**
 * Accessor interface implemented by MixinEnchantmentMenu to expose enchantSlots.
 */
public interface EnchantmentMenuSlotsAccessor {
    Container ladssmp$getEnchantSlots();
}
