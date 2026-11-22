package com.thelads.core.v1_8_9.perf.mixin;

import com.thelads.core.v1_8_9.perf.LoadPerf189;
import net.minecraft.client.renderer.texture.TextureUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Mipmaps: Minecraft's blend kept a shared buffer (one thread only) and took 16 to 20 Math.pow per pixel; LoadPerf189's gives the
 * same colours from a lookup table and is thread-safe, so every sprite's mipmaps can be built at once.
 */
@Mixin(TextureUtil.class)
public abstract class TextureUtilMipmapMixin189 {
    @Redirect(method = "generateMipmapData", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/TextureUtil;blendColors(IIIIZ)I"))
    private static int lads$blend(int a, int b, int c, int d, boolean transparent) {
        return LoadPerf189.blendColors(a, b, c, d, transparent);
    }
}
