package com.thelads.core.v1_21_11.embedded.emf.mixin.mixins.rendering.feature;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v1_21_11.embedded.emf.models.animation.state.EMFState;


import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;


@Mixin(ItemInHandLayer.class)
public class
MixinHeldItemFeatureRenderer<S extends ArmedEntityRenderState, M extends EntityModel<S> & ArmedModel>
{

    private static final String RENDER_ARM = "submitArmWithItem";

    private static final String TRANSLATE = "Lnet/minecraft/client/model/ArmedModel;translateToHand(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;)V";

    @Inject(method = RENDER_ARM,
            at = @At(value = "HEAD"))
    private void emf$setHand(final CallbackInfo ci, @Local HumanoidArm arm) {
        EMFState.isInHand = true;
        EMFState.isInLeftHand = arm == HumanoidArm.LEFT;
    }

    @Inject(method = RENDER_ARM, at = @At(value = "INVOKE", target = TRANSLATE))
    private void emf$transform(final CallbackInfo ci) {
        EMFState.isInHandItemLayerTransform = true;
        EMFState.hasDoneArmOverride = null;
    }

    @Inject(method = RENDER_ARM, at = @At(value = "INVOKE", target = TRANSLATE, shift = At.Shift.AFTER))
    private void emf$transform2(final CallbackInfo ci) {
        EMFState.isInHandItemLayerTransform = false;
    }

    @Inject(method = RENDER_ARM, at = @At(value = "TAIL"))
    private void emf$unsetHand(final CallbackInfo ci) {
        EMFState.isInHand = false;
        EMFState.isInLeftHand = null;
    }

}