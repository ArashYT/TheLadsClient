package com.thelads.core.v1_8_9.mixin;

import net.minecraft.item.ItemFood;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** AppleSkin (FoodOverlay189): whether a food can be eaten when full, and the effect it gives (rotten icons, regeneration). */
@Mixin(ItemFood.class)
public interface ItemFoodAccessor {
    @Accessor("alwaysEdible") boolean ladsAlwaysEdible();
    @Accessor("potionId") int ladsPotionId();
    @Accessor("potionDuration") int ladsPotionDuration();
    @Accessor("potionAmplifier") int ladsPotionAmplifier();
}
