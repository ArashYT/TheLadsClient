package com.thelads.core.v1_21_11.embedded.emf.mixin.mixins.rendering.model;

import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v1_21_11.embedded.emf.models.animation.state.EMFState;

@Mixin(VillagerModel.class)
public abstract class MixinVillagerModel {

    @Inject(method =
            "setupAnim(Lnet/minecraft/client/renderer/entity/state/VillagerRenderState;)V"
            , at = @At(value = "HEAD"))
    private void emf$assertLayerFactory(final CallbackInfo ci) {
        if (EMFState.state() != null)
            EMFState.state().setLayerFactory(
                net.minecraft.client.renderer.rendertype.RenderTypes
                        ::entityCutoutNoCull);
    }



}
