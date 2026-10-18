package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.GlState189;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.9.13, without OptiFine (EarlyPlugin189): Forge's fast-TESR batch is drawn after the block entities of each render pass, twice
 * a frame, and turns the item lighting off and back on around its draw (~18 GL calls each) even when no block entity put anything
 * in it, which is nearly always. An empty batch is just finished; the state it leaves (atlas, blending, no culling, shade model)
 * is still set through GlStateManager's cache, and the lighting, already on, stays on (only the light directions are not re-sent).
 */
@Mixin(TileEntityRendererDispatcher.class)
public abstract class TesrBatchMixin189 {
    @Shadow public TextureManager renderEngine;
    @Shadow(remap = false) private Tessellator batchBuffer;

    @Inject(method = "drawBatch", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void lads$emptyBatch(int pass, CallbackInfo ci) {
        if (batchBuffer.getWorldRenderer().getVertexCount() != 0 || !GlState189.lighting()) return;
        renderEngine.bindTexture(TextureMap.locationBlocksTexture);
        GlStateManager.blendFunc(770, 771);
        GlStateManager.enableBlend();
        GlStateManager.disableCull();
        GlStateManager.shadeModel(Minecraft.isAmbientOcclusionEnabled() ? 7425 : 7424);
        batchBuffer.draw(); // finishes the empty batch: no GL call
        ci.cancel();
    }
}
