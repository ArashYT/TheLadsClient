package com.thelads.core.v1_8_9.pvp.mixin;

import com.thelads.core.v1_8_9.pvp.ChunkAnimator189;
import net.minecraft.client.renderer.ChunkRenderContainer;
import net.minecraft.client.renderer.chunk.RenderChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Chunk Animator: the chunk's offset joins its translation, before each chunk layer draws. require 0: OptiFine rewrites the chunk lists. */
@Mixin(ChunkRenderContainer.class)
public abstract class ChunkAnimatorDrawMixin189 {
    @Inject(method = "preRenderChunk", at = @At("TAIL"), require = 0)
    private void lads$animate(RenderChunk chunk, CallbackInfo ci) {
        ChunkAnimator189.translate((ChunkAnimator189.Clock) chunk);
    }
}
