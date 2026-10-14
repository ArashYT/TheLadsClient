package com.thelads.core.v1_8_9.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.IIconCreator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResourceManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents the main block TextureMap from stitching twice during a resource reload when an
 * {@link IIconCreator} is present: {@code TextureManager.onResourceManagerReload} calls
 * {@code loadTexture} before {@code ModelManager.onResourceManagerReload} calls {@code loadSprites}
 * to stitch the full block/item model registry. Setting {@code skipFirst = true} when
 * {@code iconCreator != null} defers stitching to {@code ModelManager}'s pass without breaking
 * Forge's default {@code iconCreator == null} path.
 */
@Mixin(TextureMap.class)
public abstract class TextureMapReloadMixin189 {
    @Shadow @Final private IIconCreator iconCreator;
    @Shadow private boolean skipFirst;

    @Inject(method = "loadTexture", at = @At("HEAD"))
    private void ladsSkipRedundantBlockAtlasPass(IResourceManager resourceManager, CallbackInfo ci) {
        if (this.iconCreator != null) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null && (Object) this == mc.getTextureMapBlocks()) {
                this.skipFirst = true;
            }
        }
    }
}
