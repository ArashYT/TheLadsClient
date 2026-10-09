package com.thelads.core.v1_20_1.game.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.v1_20_1.game.anim.NativeOldAnimations1201;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 1.7 Animations' third person: a flat held item in 1.7's placement from the arm pivot, and no shield while the sword blocks. */
@Mixin(ItemInHandLayer.class)
public abstract class OldAnimationsHeldItemMixin {
    @WrapOperation(method = "renderArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;"
        + "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;"
        + "ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"), require = 1)
    private void lads$oldHeldItem(ItemInHandRenderer self, LivingEntity entity, ItemStack stack, ItemDisplayContext context, boolean left,
                                  PoseStack pose, MultiBufferSource buffers, int light, Operation<Void> original,
                                  @Local(argsOnly = true) HumanoidArm arm) {
        if (NativeOldAnimations1201.hideShield(entity, stack)) return;
        ArmedModel armed = ((ItemInHandLayer<?, ?>) (Object) this).getParentModel();
        boolean old = NativeOldAnimations1201.thirdPersonItem(entity, stack, arm, pose, armed);
        original.call(self, entity, stack, old ? ItemDisplayContext.NONE : context, left, pose, buffers, light);
    }
}
