package com.thelads.core.v26_2.feature.food.mixin;

import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** FoodData keeps exhaustion private: NativeFood reads the integrated server player's (the setter is for QA captures). */
@Mixin(FoodData.class)
public interface FoodDataAccess {
    @Accessor("exhaustionLevel") float lads$exhaustion();
    @Accessor("exhaustionLevel") void lads$setExhaustion(float exhaustion);
}
