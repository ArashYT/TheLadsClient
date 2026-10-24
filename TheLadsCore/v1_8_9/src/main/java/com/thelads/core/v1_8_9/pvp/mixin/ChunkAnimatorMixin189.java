package com.thelads.core.v1_8_9.pvp.mixin;

import com.thelads.core.v1_8_9.pvp.ChunkAnimator189;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.util.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Chunk Animator: a render chunk moved to a new position (newly in view) starts its animation on its next draw. require 0: OptiFine rewrites RenderChunk. */
@Mixin(RenderChunk.class)
public abstract class ChunkAnimatorMixin189 implements ChunkAnimator189.Clock {
    @Unique private long lads$shownAt;

    @Override public long lads$shownAt() { return lads$shownAt; }

    @Override public void lads$setShownAt(long nanos) { lads$shownAt = nanos; }

    @Inject(method = "setPosition", at = @At("HEAD"), require = 0)
    private void lads$newPosition(BlockPos pos, CallbackInfo ci) {
        lads$shownAt = 0;
    }
}
