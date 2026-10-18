package com.thelads.smp.features.arise.nb9;

import net.minecraft.world.item.ItemStack;

/**
 * Interface injected into SignBlockEntity to store and persist items displayed on hanging signs.
 */
public interface HangingSignItemHolder {
    ItemStack ladssmp$getDisplayedItem();
    void ladssmp$setDisplayedItem(ItemStack stack);
}
