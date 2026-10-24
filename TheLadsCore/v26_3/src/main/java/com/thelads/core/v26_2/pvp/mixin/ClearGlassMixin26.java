package com.thelads.core.v26_2.pvp.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import com.thelads.core.v26_2.pvp.PvpVisuals26;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Clear Glass: a glass texture is edited as it loads, before its mipmaps are made (the 3-argument constructor ends up here too). */
@Mixin(SpriteContents.class)
public abstract class ClearGlassMixin26 {
    @Inject(method = "<init>(Lnet/minecraft/resources/Identifier;Lnet/minecraft/client/resources/metadata/animation/FrameSize;Lcom/mojang/blaze3d/platform/NativeImage;Ljava/util/Optional;Ljava/util/List;Ljava/util/Optional;)V",
        at = @At("TAIL"))
    private void lads$clearGlass(Identifier name, FrameSize frame, NativeImage image, Optional<AnimationMetadataSection> animation,
            List<MetadataSectionType.WithValue<?>> metadata, Optional<TextureMetadataSection> texture, CallbackInfo ci) {
        PvpVisuals26.glassLoaded(name, frame.width(), frame.height(), image);
    }
}
