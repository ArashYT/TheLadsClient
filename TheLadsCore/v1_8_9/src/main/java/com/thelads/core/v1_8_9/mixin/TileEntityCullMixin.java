package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.EntityCulling189;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Entity Culling: a block entity (chest, sign, skull, banner...) hidden behind blocks is not drawn in the world pass. Breaking
 * overlays always draw. OptiFine patches this class: require = 0 leaves everything drawn if the method ever moves.
 */
@Mixin(TileEntityRendererDispatcher.class)
public abstract class TileEntityCullMixin {
    @Inject(method = "renderTileEntity", at = @At("HEAD"), cancellable = true, require = 0)
    private void ladsCull(TileEntity tile, float partialTicks, int destroyStage, CallbackInfo ci) {
        if (EntityCulling189.skip(tile, destroyStage, TileEntityRendererDispatcher.staticPlayerX, TileEntityRendererDispatcher.staticPlayerY,
            TileEntityRendererDispatcher.staticPlayerZ)) ci.cancel();
    }
}
