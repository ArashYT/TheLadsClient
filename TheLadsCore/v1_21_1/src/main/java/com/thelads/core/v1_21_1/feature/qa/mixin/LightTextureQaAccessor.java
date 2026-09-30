package com.thelads.core.v1_21_1.feature.qa.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 1.21.1 computes the lightmap on the CPU before uploading it; QA reads the texels the world renders with. */
@Mixin(LightTexture.class)
public interface LightTextureQaAccessor {
    @Accessor("lightPixels") NativeImage ladsQaLightPixels();
}
