package com.thelads.core.mixin.alwayson.skinlayers;

import com.mojang.blaze3d.platform.NativeImage;
import com.thelads.core.features.alwayson.skinlayers.accessor.NativeImageAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(NativeImage.class)
public abstract class NativeImageMixin implements NativeImageAccessor {
    @Shadow
    private long pixels;

    @Override
    public boolean skinlayers$isAllocated() {
        return this.pixels != 0L;
    }
}
