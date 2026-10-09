package com.thelads.core.v1_20_1.render.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lads Kerria (1.8.5): the native pixel address PboUpload1201 copies frames from. */
@Mixin(NativeImage.class)
public interface NativeImageAnimationAccess {
    @Accessor("pixels") long lads$pixels();
}
