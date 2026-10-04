package com.thelads.core.v1_8_9.mixin;

import net.minecraft.item.ItemFood;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Food189: whether a food is edible when full, and the effect eating it gives (green icons for harmful ones, Regeneration's health). */
@Mixin(ItemFood.class)
public interface ItemFoodFields {
    @Accessor("alwaysEdible") boolean ladsAlwaysEdible();
    @Accessor("potionId") int ladsPotionId();
    @Accessor("potionDuration") int ladsPotionDuration();
    @Accessor("potionAmplifier") int ladsPotionAmplifier();
}
