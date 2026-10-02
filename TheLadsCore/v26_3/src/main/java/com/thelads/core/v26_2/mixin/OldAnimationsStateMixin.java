package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.NativeOldAnimations;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public class OldAnimationsStateMixin implements NativeOldAnimations.State {
    @Unique private int lads$oldAnimations;
    @Override public int lads$oldAnimations() { return lads$oldAnimations; }
    @Override public void lads$oldAnimations(int flags) { lads$oldAnimations = flags; }
}
