package com.thelads.core.v26_2.mixin.ebe;

import com.thelads.core.v26_2.feature.ebe.BakedBlockEntities263;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Baked block entities skip their renderer while the chunk mesh shows them (vanilla and Sodium extract through here). */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class EbeBlockEntityRenderDispatcherMixin {
    @Inject(method = "tryExtractRenderState", at = @At("HEAD"), cancellable = true)
    private void lads$ebeSkipBaked(BlockEntity blockEntity, float partialTicks, ModelFeatureRenderer.CrumblingOverlay breakProgress,
                                   boolean isGloballyRendered, CallbackInfoReturnable<BlockEntityRenderState> cir) {
        if (BakedBlockEntities263.skipRenderer(blockEntity)) cir.setReturnValue(null);
    }
}
