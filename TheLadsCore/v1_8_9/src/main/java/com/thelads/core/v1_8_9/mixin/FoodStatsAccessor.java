package com.thelads.core.v1_8_9.mixin;

import net.minecraft.util.FoodStats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** FoodStats keeps exhaustion private: Food189 reads the integrated server player's. */
@Mixin(FoodStats.class)
public interface FoodStatsAccessor {
    @Accessor("foodExhaustionLevel") float ladsExhaustion();
}
