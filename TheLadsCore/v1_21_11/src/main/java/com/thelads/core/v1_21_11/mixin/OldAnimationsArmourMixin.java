package com.thelads.core.v1_21_11.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.v1_21_11.feature.NativeOldAnimations;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Red armour on hurt: the armour pieces of one submit take the body's hurt overlay (OldAnimationsEquipmentMixin applies it). */
@Mixin(HumanoidArmorLayer.class)
public class OldAnimationsArmourMixin {
    @Unique private static final String SUBMIT = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V";

    @Inject(method = SUBMIT, at = @At("HEAD"), require = 1)
    private void lads$redArmour(PoseStack pose, SubmitNodeCollector collector, int light, HumanoidRenderState state, float yaw, float pitch, CallbackInfo ci) {
        NativeOldAnimations.armourPieces(state);
    }

    @Inject(method = SUBMIT, at = @At("TAIL"), require = 1)
    private void lads$redArmourEnd(PoseStack pose, SubmitNodeCollector collector, int light, HumanoidRenderState state, float yaw, float pitch, CallbackInfo ci) {
        NativeOldAnimations.armourPieces(null);
    }
}
