package com.thelads.core.v1_8_9.mixin.ebe;

import com.thelads.core.v1_8_9.feature.ebe.BakedBlockEntities189;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Baked chests skip their renderer while the chunk mesh shows them (breaking cracks still draw through it). */
@Mixin(TileEntityRendererDispatcher.class)
public abstract class EbeTileEntityRendererDispatcherMixin {
    @Inject(method = "renderTileEntityAt(Lnet/minecraft/tileentity/TileEntity;DDDFI)V", at = @At("HEAD"), cancellable = true)
    private void lads$ebeSkipBaked(TileEntity entity, double x, double y, double z, float partialTicks, int destroyStage, CallbackInfo ci) {
        if (BakedBlockEntities189.skipRenderer(entity, destroyStage)) ci.cancel();
    }
}
