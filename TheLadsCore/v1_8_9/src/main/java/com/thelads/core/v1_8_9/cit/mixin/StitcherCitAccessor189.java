package com.thelads.core.v1_8_9.cit.mixin;

import java.util.List;
import java.util.Set;
import net.minecraft.client.renderer.texture.Stitcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Custom Item Textures: what Cit189.refit needs to leave CIT textures out of a full atlas and stitch again. */
@Mixin(Stitcher.class)
public interface StitcherCitAccessor189 {
    @Accessor("setStitchHolders") Set<Stitcher.Holder> lads$holders();
    @Accessor("stitchSlots") List<?> lads$slots();
    @Accessor("currentWidth") void lads$setWidth(int width);
    @Accessor("currentHeight") void lads$setHeight(int height);
    @Accessor("maxWidth") int lads$maxWidth();
    @Accessor("maxHeight") int lads$maxHeight();
}
