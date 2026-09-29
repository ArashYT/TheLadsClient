package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.VerticalBobState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(CameraRenderState.class)
public class VerticalBobStateMixin implements VerticalBobState {
    @Unique private float lads$verticalBob;
    @Override public float lads$verticalBob() { return lads$verticalBob; }
    @Override public void lads$verticalBob(float displacement) { lads$verticalBob = displacement; }
}
