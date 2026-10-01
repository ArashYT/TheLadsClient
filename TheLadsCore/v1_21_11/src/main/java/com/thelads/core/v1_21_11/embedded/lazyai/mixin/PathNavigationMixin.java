package com.thelads.core.v1_21_11.embedded.lazyai.mixin;

import com.thelads.core.v1_21_11.embedded.lazyai.LazyAi;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Vanilla recomputes a blocked path at most once per 20 ticks; far mobs wait up to four times longer. */
@Mixin(PathNavigation.class)
public abstract class PathNavigationMixin {
    @Shadow @Final protected Mob mob;

    @ModifyConstant(method = "recomputePath", constant = @Constant(longValue = 20L))
    private long lads$lazyRecompute(long ticks) {
        return ticks * LazyAi.multiplier(mob);
    }
}
