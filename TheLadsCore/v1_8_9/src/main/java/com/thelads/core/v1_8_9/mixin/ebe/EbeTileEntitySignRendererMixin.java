package com.thelads.core.v1_8_9.mixin.ebe;

import com.thelads.core.v1_8_9.feature.ebe.BakedBlockEntities189;
import net.minecraft.client.model.ModelSign;
import net.minecraft.client.renderer.tileentity.TileEntitySignRenderer;
import net.minecraft.tileentity.TileEntitySign;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Signs: the board is in the chunk mesh, the renderer keeps drawing only the text. */
@Mixin(TileEntitySignRenderer.class)
public abstract class EbeTileEntitySignRendererMixin {
    @Inject(method = "renderTileEntityAt(Lnet/minecraft/tileentity/TileEntitySign;DDDFI)V", at = @At("HEAD"))
    private void lads$ebeSignStart(TileEntitySign sign, double x, double y, double z, float partialTicks, int destroyStage, CallbackInfo ci) {
        BakedBlockEntities189.signBoardBaked = BakedBlockEntities189.signBoardBaked(sign, destroyStage);
    }

    @Redirect(method = "renderTileEntityAt(Lnet/minecraft/tileentity/TileEntitySign;DDDFI)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/ModelSign;renderSign()V"))
    private void lads$ebeSkipBoard(ModelSign model) {
        if (!BakedBlockEntities189.signBoardBaked) model.renderSign();
    }
}
