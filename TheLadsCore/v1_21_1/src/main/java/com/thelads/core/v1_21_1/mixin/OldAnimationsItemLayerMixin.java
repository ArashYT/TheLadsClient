package com.thelads.core.v1_21_1.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.v1_21_1.feature.NativeOldAnimations;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 1.7 third-person items: a player's flat item goes where 1.7 held it (EMF's hooks around translateToHand stay untouched),
 * and the blocking shield is hidden during the sword block.
 */
@Mixin(ItemInHandLayer.class)
public class OldAnimationsItemLayerMixin {
    @ModifyArg(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/layers/ItemInHandLayer;renderArmWithItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"),
        index = 1, require = 1)
    private ItemStack lads$hideShield(LivingEntity entity, ItemStack stack, ItemDisplayContext context, HumanoidArm arm, PoseStack pose,
                                      MultiBufferSource buffers, int light) {
        return NativeOldAnimations.layerStack(entity, stack, arm);
    }

    @WrapOperation(method = "renderArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"),
        require = 1)
    private void lads$oldItem(ItemInHandRenderer renderer, LivingEntity entity, ItemStack stack, ItemDisplayContext context, boolean left, PoseStack pose,
                              MultiBufferSource buffers, int light, Operation<Void> original) {
        boolean blocking = ((RenderLayer<?, ?>) (Object) this).getParentModel() instanceof HumanoidModel<?> model
            && (left ? model.leftArmPose : model.rightArmPose) == HumanoidModel.ArmPose.BLOCK;
        if (!NativeOldAnimations.thirdPersonItem(renderer, entity, stack, left, blocking, pose, buffers, light))
            original.call(renderer, entity, stack, context, left, pose, buffers, light);
    }
}
