package com.thelads.core.v26_2.render.mixin;

import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lads Kerria (1.8.5): SpriteContents' private AnimatedTexture, read by AnimationStateAnimationMixin. */
@Mixin(targets = "net.minecraft.client.renderer.texture.SpriteContents$AnimatedTexture")
public interface AnimatedTextureAnimationAccess {
    @Accessor("interpolateFrames") boolean lads$interpolates();
    @Accessor("frames") List<?> lads$frames();
}
