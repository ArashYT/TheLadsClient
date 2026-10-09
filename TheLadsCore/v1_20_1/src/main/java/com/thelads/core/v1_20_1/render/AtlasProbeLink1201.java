package com.thelads.core.v1_20_1.render;

import java.util.List;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/** QA (1.8.5 render capture), added to TextureAtlas by TextureAtlasAnimationMixin. */
public interface AtlasProbeLink1201 {
    int lads$width();
    int lads$height();
    int lads$mipLevel();
    /** The sprites with more than one animation frame. */
    List<TextureAtlasSprite> lads$animatedSprites();
}
