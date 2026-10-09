package com.thelads.core.v26_2.render.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lads Kerria (1.8.5): the frame index of SpriteContents' private FrameInfo record. */
@Mixin(targets = "net.minecraft.client.renderer.texture.SpriteContents$FrameInfo")
public interface FrameInfoAnimationAccess {
    @Accessor("index") int lads$index();
}
