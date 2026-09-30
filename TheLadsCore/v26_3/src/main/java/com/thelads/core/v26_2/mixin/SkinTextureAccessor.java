package com.thelads.core.v26_2.mixin;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SkinTextureDownloader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(SkinTextureDownloader.class)
public interface SkinTextureAccessor {
    @Invoker("processLegacySkin") static NativeImage ladsNormalizeSkin(NativeImage image,String name){throw new AssertionError();}
}
