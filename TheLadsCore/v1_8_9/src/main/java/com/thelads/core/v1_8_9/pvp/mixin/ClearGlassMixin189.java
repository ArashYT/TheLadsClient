package com.thelads.core.v1_8_9.pvp.mixin;

import com.thelads.core.v1_8_9.pvp.PvpVisuals189;
import java.awt.image.BufferedImage;
import java.util.List;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.data.AnimationMetadataSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Clear Glass: a glass sprite's frames are edited once loaded, before TextureMap makes their mipmaps. require 0: OptiFine rewrites this class. */
@Mixin(TextureAtlasSprite.class)
public abstract class ClearGlassMixin189 {
    @Shadow protected List<int[][]> framesTextureData;
    @Shadow protected int width;
    @Shadow protected int height;
    @Shadow public abstract String getIconName();

    @Inject(method = "loadSprite", at = @At("TAIL"), require = 0)
    private void lads$clearGlass(BufferedImage[] images, AnimationMetadataSection animation, CallbackInfo ci) {
        PvpVisuals189.glassLoaded(getIconName(), framesTextureData, width, height);
    }
}
