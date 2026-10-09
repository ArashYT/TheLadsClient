package com.thelads.core.v26_2.render.mixin;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lads Kerria (1.8.5): a sprite's anisotropic-filtering padding (copies only cover unpadded sprites). */
@Mixin(TextureAtlasSprite.class)
public interface TextureAtlasSpriteAnimationAccess {
    @Accessor("padding") int lads$padding();
}
