package com.thelads.core.v26_2.feature.pvp.mixin;

import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Hit Color: the entity overlay texture, whose hurt rows PvpVisuals26 repaints. */
@Mixin(OverlayTexture.class)
public interface OverlayTextureAccessor26 {
    @Accessor("texture")
    DynamicTexture lads$texture();
}
