package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.rentities.RentitiesRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Rentities: a model submit that was recorded for an instanced draw skips the vanilla per-entity vertex build. */
@Mixin(ModelFeatureRenderer.class)
public class RentitiesModelFeatureMixin {
    @Inject(method = "prepareModel", at = @At("HEAD"), cancellable = true, require = 0)
    private <S> void ladsRentities(ModelFeatureRenderer.Submit<S> submit, CallbackInfo ci) {
        if (RentitiesRenderer.record(submit)) ci.cancel();
    }
}
