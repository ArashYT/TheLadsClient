package com.thelads.core.v26_2.feature.mapframes.mixin;

import com.thelads.core.v26_2.feature.mapframes.MapFrameState26;
import net.minecraft.client.renderer.entity.state.ItemFrameRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ItemFrameRenderState.class)
abstract class MapFrameStateMixin26 implements MapFrameState26 {
    @Unique private boolean lads$mapFrameHidden;

    @Override public boolean lads$mapFrameHidden() { return lads$mapFrameHidden; }
    @Override public void lads$mapFrameHidden(boolean hidden) { lads$mapFrameHidden = hidden; }
}
