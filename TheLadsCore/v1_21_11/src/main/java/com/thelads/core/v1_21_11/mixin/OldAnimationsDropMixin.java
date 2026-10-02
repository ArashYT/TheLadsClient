package com.thelads.core.v1_21_11.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.v1_21_11.feature.NativeOldAnimations;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 2D dropped items: extraction resolves a flat item with no display transform, submit draws it as 1.7's camera-facing icon. */
@Mixin(ItemEntityRenderer.class)
public abstract class OldAnimationsDropMixin extends EntityRenderer<ItemEntity, ItemEntityRenderState> {
    private OldAnimationsDropMixin(EntityRendererProvider.Context context) {
        super(context);
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;F)V",
        at = @At("TAIL"), require = 1)
    private void lads$flatDropState(ItemEntity entity, ItemEntityRenderState state, float partial, CallbackInfo ci) {
        NativeOldAnimations.droppedItem(entity, state);
    }

    /** The name tag pass (super.submit) still runs. */
    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
        at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$flatDrop(ItemEntityRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        if (!NativeOldAnimations.droppedItem(state, pose, collector)) return;
        super.submit(state, pose, collector, camera);
        ci.cancel();
    }
}
