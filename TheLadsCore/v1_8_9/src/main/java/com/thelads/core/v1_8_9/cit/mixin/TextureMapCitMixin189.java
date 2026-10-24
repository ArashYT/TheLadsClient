package com.thelads.core.v1_8_9.cit.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.v1_8_9.cit.Cit189;
import net.minecraft.client.renderer.StitcherException;
import net.minecraft.client.renderer.texture.Stitcher;
import net.minecraft.client.renderer.texture.TextureMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Custom Item Textures: an atlas too full for the packs' CIT textures stitches again without (some of) them instead of failing the
 * resource reload (Minecraft would drop every pack, or crash at startup).
 */
@Mixin(TextureMap.class)
public abstract class TextureMapCitMixin189 {
    @WrapOperation(method = "loadTextureAtlas", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/Stitcher;doStitch()V"))
    private void lads$citFit(Stitcher stitcher, Operation<Void> original) {
        try {
            original.call(stitcher);
        } catch (StitcherException full) {
            Cit189.refit(stitcher, full, () -> original.call(stitcher));
        }
    }
}
