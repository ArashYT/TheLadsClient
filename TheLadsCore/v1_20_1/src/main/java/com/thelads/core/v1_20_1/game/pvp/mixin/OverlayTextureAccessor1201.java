package com.thelads.core.v1_20_1.game.pvp.mixin;

import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Hit Color: the entity overlay texture, whose hurt rows PvpVisuals1201 repaints. */
@Mixin(OverlayTexture.class)
public interface OverlayTextureAccessor1201 {
    @Accessor("texture")
    DynamicTexture lads$texture();
}
