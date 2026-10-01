package com.thelads.core.v26_2.embedded.etf.mixin.mixins.entity.renderer.feature;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v26_2.embedded.etf.features.state.ETFState;
import net.minecraft.client.renderer.entity.layers.WingsLayer;

@Mixin(WingsLayer.class)
public abstract class MixinElytraFeatureRenderer<T extends LivingEntity> {

    @Inject(method =
            "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V"
            ,
            at = @At(value = "HEAD"))
    private void etf$markPatchable(CallbackInfo ci) {
        ETFState.allowTexturePatching = true;
    }

    @Inject(method =
            "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V"
            ,
            at = @At(value = "RETURN"))
    private void etf$markPatchableEnd(CallbackInfo ci) {
        ETFState.allowTexturePatching = false;
    }
}


