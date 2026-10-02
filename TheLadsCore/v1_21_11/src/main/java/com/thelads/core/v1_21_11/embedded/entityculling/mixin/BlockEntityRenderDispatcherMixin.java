package com.thelads.core.v1_21_11.embedded.entityculling.mixin;

import com.thelads.core.v1_21_11.embedded.entityculling.EntityCulling;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla and Sodium both extract block entity render states through here; occluded ones extract nothing. */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {
    @Inject(method = "tryExtractRenderState", at = @At("HEAD"), cancellable = true)
    private void lads$occlusionCull(BlockEntity blockEntity, float partialTick, ModelFeatureRenderer.CrumblingOverlay crumbling,
                                    CallbackInfoReturnable<BlockEntityRenderState> cir) {
        if (EntityCulling.shadowPass()) return;
        BlockEntityRenderer<BlockEntity, BlockEntityRenderState> renderer = ((BlockEntityRenderDispatcher) (Object) this).getRenderer(blockEntity);
        if (renderer == null || renderer.shouldRenderOffScreen()) return;
        if (!EntityCulling.drawBlockEntity(blockEntity)) cir.setReturnValue(null);
    }
}
