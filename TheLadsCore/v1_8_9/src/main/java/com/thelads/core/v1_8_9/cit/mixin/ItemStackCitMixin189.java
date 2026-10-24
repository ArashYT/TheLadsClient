package com.thelads.core.v1_8_9.cit.mixin;

import com.thelads.core.v1_8_9.cit.Cit189;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Custom Item Textures: each stack keeps its last lookup, so drawing it again does no matching. */
@Mixin(ItemStack.class)
public abstract class ItemStackCitMixin189 implements Cit189.Cached {
    @Unique
    private Cit189.Match lads$citMatch;

    @Override
    public Cit189.Match lads$cit() {
        return lads$citMatch;
    }

    @Override
    public void lads$cit(Cit189.Match match) {
        lads$citMatch = match;
    }
}
