package com.thelads.smp.mixin;

import com.thelads.smp.config.LadsConfig;
import net.minecraft.world.food.FoodProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Consolidates 'always_eat': Allows eating food even when the hunger bar is full.
 */
@Mixin(FoodProperties.class)
public abstract class MixinFoodProperties {

    @Inject(method = "canAlwaysEat", at = @At("RETURN"), cancellable = true)
    private void ladssmp$allowAlwaysEating(CallbackInfoReturnable<Boolean> cir) {
        if (LadsConfig.get().features.alwaysEat) {
            cir.setReturnValue(true);
        }
    }
}
