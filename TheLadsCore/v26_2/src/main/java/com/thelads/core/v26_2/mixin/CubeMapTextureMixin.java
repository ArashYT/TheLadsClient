package com.thelads.core.v26_2.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import com.thelads.core.client.title.PanoramaPicker;
import java.io.IOException;
import net.minecraft.client.renderer.texture.CubeMapTexture;
import net.minecraft.client.renderer.texture.TextureContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Panorama Version: the title cube map's six sides come from the picked release (over any resource pack), else as normal. */
@Mixin(CubeMapTexture.class)
public abstract class CubeMapTextureMixin {
    @Redirect(method = "loadContents", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/TextureContents;load(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/texture/TextureContents;"), require = 1)
    private TextureContents ladsPickedPanorama(ResourceManager resources, Identifier id) throws IOException {
        byte[] picked = "minecraft".equals(id.getNamespace()) ? PanoramaPicker.image(id.getPath()) : null;
        return picked != null ? new TextureContents(NativeImage.read(picked), null) : TextureContents.load(resources, id);
    }
}
