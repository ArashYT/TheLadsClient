package com.thelads.core.v1_20_1.pvp.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import com.thelads.core.v1_20_1.pvp.PvpVisuals1201;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Clear Glass: a glass texture is edited as it loads, before its mipmaps are made. */
@Mixin(SpriteContents.class)
public abstract class ClearGlassMixin1201 {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void lads$clearGlass(ResourceLocation name, FrameSize frame, NativeImage image, AnimationMetadataSection animation, CallbackInfo ci) {
        PvpVisuals1201.glassLoaded(name, frame.width(), frame.height(), image);
    }
}
