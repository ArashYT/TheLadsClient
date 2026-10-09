package com.thelads.core.v26_2.render.mixin;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.thelads.core.v26_2.render.AnimationStateLink26;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.List;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lads Kerria (1.8.5): keeps the state's private AnimatedTexture so AnimationCopy26 can read its frames and interpolation flag. */
@Mixin(SpriteContents.AnimationState.class)
public abstract class AnimationStateAnimationMixin implements AnimationStateLink26 {
    @Shadow private int frame;
    @Shadow @Final private Int2ObjectMap<GpuTextureView> frameTexturesByIndex;
    @Unique private Object lads$animation;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void lads$keep(SpriteContents owner, @Coerce Object animationInfo, Int2ObjectMap<GpuTextureView> frames, GpuBufferSlice[] ubos, CallbackInfo ci) {
        lads$animation = animationInfo;
    }

    @Override public boolean lads$interpolates() {
        return ((AnimatedTextureAnimationAccess) lads$animation).lads$interpolates();
    }

    @Override public GpuTextureView lads$currentFrame() {
        List<?> frames = ((AnimatedTextureAnimationAccess) lads$animation).lads$frames();
        return frameTexturesByIndex.get(((FrameInfoAnimationAccess) frames.get(frame)).lads$index());
    }
}
