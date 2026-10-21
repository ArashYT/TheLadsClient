package com.thelads.core.v1_8_9.mixin.optifine;

import com.thelads.core.modules.OptiFine189Module;
import net.minecraft.world.WorldProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * OptiFine Cloud Height setting:
 * Modifies WorldProvider.getCloudHeight() which controls vanilla cloud rendering
 * and coordinates with LadsClouds189 (which reads world.provider.getCloudHeight()).
 */
@Mixin(WorldProvider.class)
public abstract class WorldProviderCloudHeightMixin189 {
    @Inject(method = "getCloudHeight", at = @At("HEAD"), cancellable = true)
    private void lads$cloudHeight(CallbackInfoReturnable<Float> cir) {
        float customHeight = OptiFine189Module.sOfCloudHeight;
        if (customHeight != 128.0f) {
            cir.setReturnValue(customHeight);
        }
    }
}
