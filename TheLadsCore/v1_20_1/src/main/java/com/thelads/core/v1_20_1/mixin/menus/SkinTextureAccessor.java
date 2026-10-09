package com.thelads.core.v1_20_1.mixin.menus;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.HttpTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Vanilla's own 64x32 to 64x64 skin conversion (and size check); LocalSkins1201 calls it on a throw-away texture object. */
@Mixin(HttpTexture.class)
public interface SkinTextureAccessor {
    @Invoker("processLegacySkin") NativeImage ladsProcessLegacySkin(NativeImage image);
}
