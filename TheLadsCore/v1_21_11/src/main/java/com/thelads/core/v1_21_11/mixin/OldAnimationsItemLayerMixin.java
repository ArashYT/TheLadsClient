package com.thelads.core.v1_21_11.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.v1_21_11.feature.NativeOldAnimations;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 1.7 third-person items: a held item extracted flat is submitted where 1.7 held it (EMF's hooks around translateToHand stay untouched). */
@Mixin(ItemInHandLayer.class)
public class OldAnimationsItemLayerMixin {
    @WrapOperation(method = "submitArmWithItem", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"),
        require = 1)
    private void lads$oldItem(ItemStackRenderState item, PoseStack pose, SubmitNodeCollector collector, int light, int overlay, int outline, Operation<Void> original,
                              @Local(argsOnly = true) ArmedEntityRenderState state, @Local(argsOnly = true) ItemStack stack, @Local(argsOnly = true) HumanoidArm arm) {
        NativeOldAnimations.thirdPersonItem(item, stack, arm, state, pose);
        if (!NativeOldAnimations.recorded(item.displayContext, pose)) original.call(item, pose, collector, light, overlay, outline);
    }
}
