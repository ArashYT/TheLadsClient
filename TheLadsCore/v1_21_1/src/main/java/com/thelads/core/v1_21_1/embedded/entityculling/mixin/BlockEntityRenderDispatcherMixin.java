package com.thelads.core.v1_21_1.embedded.entityculling.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.v1_21_1.embedded.entityculling.EntityCulling;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla and Sodium both draw block entities through here; occluded ones are skipped. */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void lads$occlusionCull(BlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource buffers, CallbackInfo ci) {
        if (EntityCulling.shadowPass()) return;
        BlockEntityRenderer<BlockEntity> renderer = ((BlockEntityRenderDispatcher) (Object) this).getRenderer(blockEntity);
        if (renderer == null || renderer.shouldRenderOffScreen(blockEntity)) return;
        if (!EntityCulling.drawBlockEntity(blockEntity)) ci.cancel();
    }
}
