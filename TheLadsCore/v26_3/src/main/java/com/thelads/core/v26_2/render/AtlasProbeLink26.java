package com.thelads.core.v26_2.render;

import java.util.List;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/** QA (1.8.5 render capture), added to TextureAtlas by TextureAtlasAnimationMixin. */
public interface AtlasProbeLink26 {
    /** The animated sprites, in the order of the atlas's animation states. */
    List<TextureAtlasSprite> lads$animatedSprites();

    /** Every animation state marked dirty, then the atlas's own animation upload (copies or the game's draws, per BuiltInRender). */
    void lads$redrawAll();

    int lads$maxMipLevel();
}
