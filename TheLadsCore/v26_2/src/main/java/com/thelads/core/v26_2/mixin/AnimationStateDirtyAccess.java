package com.thelads.core.v26_2.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.client.renderer.texture.SpriteContents;

/** SpriteContents.AnimationState's "changed since the last draw" flag, set by SodiumAnimateAllMixin. */
@Mixin(SpriteContents.AnimationState.class)
public interface AnimationStateDirtyAccess {
    @Accessor("isDirty") void lads$dirty(boolean dirty);
}
