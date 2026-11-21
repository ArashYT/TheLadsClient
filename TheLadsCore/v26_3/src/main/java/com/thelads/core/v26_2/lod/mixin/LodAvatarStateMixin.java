package com.thelads.core.v26_2.lod.mixin;

import com.thelads.core.v26_2.lod.LodFlags26;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Player LOD bits carried on the avatar render state. */
@Mixin(AvatarRenderState.class)
abstract class LodAvatarStateMixin implements LodFlags26 {
    @Unique private int lads$lod;

    @Override public int lads$lod() { return lads$lod; }
    @Override public void lads$lod(int bits) { lads$lod = bits; }
}
