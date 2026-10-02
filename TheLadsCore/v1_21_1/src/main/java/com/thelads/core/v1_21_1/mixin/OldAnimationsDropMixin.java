package com.thelads.core.v1_21_1.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.v1_21_1.feature.NativeOldAnimations;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 2D dropped items: a flat item is 1.7's camera-facing icon; the name tag pass (super.render) still runs. */
@Mixin(ItemEntityRenderer.class)
public abstract class OldAnimationsDropMixin extends EntityRenderer<ItemEntity> {
    private OldAnimationsDropMixin(EntityRendererProvider.Context context) {
        super(context);
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/item/ItemEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$flatDrop(ItemEntity entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (!NativeOldAnimations.droppedItem(entity, partial, pose, buffers, light)) return;
        super.render(entity, yaw, partial, pose, buffers, light);
        ci.cancel();
    }
}
