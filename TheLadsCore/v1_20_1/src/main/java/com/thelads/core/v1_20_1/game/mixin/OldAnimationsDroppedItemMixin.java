package com.thelads.core.v1_20_1.game.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.v1_20_1.game.anim.NativeOldAnimations1201;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntityRenderer.class)
public class OldAnimationsDroppedItemMixin {
    @Inject(method = "render(Lnet/minecraft/world/entity/item/ItemEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;"
        + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"))
    private void lads$begin(ItemEntity entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo callback) {
        NativeOldAnimations1201.droppedBegin();
    }

    // 1.7 Animations' 2D dropped items: one flat 1.7 icon facing the camera instead of vanilla's spinning cluster.
    @WrapOperation(method = "render(Lnet/minecraft/world/entity/item/ItemEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;"
        + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/ItemRenderer;"
        + "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;"
        + "Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V"), require = 1)
    private void lads$oldDropped(ItemRenderer self, ItemStack stack, ItemDisplayContext context, boolean left, PoseStack pose,
                                 MultiBufferSource buffers, int light, int overlay, BakedModel model, Operation<Void> original,
                                 @Local(argsOnly = true, ordinal = 1) float partial, @Local(argsOnly = true) ItemEntity entity) {
        Boolean flat = NativeOldAnimations1201.droppedItem(entity, model, pose, partial);
        if (flat == null) return;
        original.call(self, stack, flat ? ItemDisplayContext.NONE : context, left, pose, buffers, light, overlay, model);
    }
}
