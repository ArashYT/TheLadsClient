package com.thelads.core.v26_2.feature.paperdoll.mixin;

import com.thelads.core.v26_2.feature.paperdoll.PaperDollRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public abstract class PaperDollStateMixin implements PaperDollRenderState {
    @Unique private int ladsPaperDollAlpha = 255;
    @Override public int ladsPaperDollAlpha() { return ladsPaperDollAlpha; }
    @Override public void ladsSetPaperDollAlpha(int value) { ladsPaperDollAlpha = Math.clamp(value, 0, 255); }
}
