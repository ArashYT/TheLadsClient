package com.thelads.core.v1_21_1.embedded.emf.mixin.mixins.rendering.model;

import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v1_21_1.embedded.emf.models.animation.state.EMFState;

@Mixin(VillagerModel.class)
public abstract class MixinVillagerModel {

    @Inject(method =
            "setupAnim"
            , at = @At(value = "HEAD"))
    private void emf$assertLayerFactory(final CallbackInfo ci) {
        if (EMFState.state() != null)
            EMFState.state().setLayerFactory(
                RenderType
                        ::entityCutoutNoCull);
    }



}
