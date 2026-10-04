package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.NativeItemPhysics;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ItemEntityRenderState.class)
public class ItemPhysicsStateMixin implements NativeItemPhysics.Posed {
    @Unique private float lads$yaw, lads$raise, lads$pitch = Float.NaN;
    @Override public float lads$yaw() { return lads$yaw; }
    @Override public float lads$pitch() { return lads$pitch; }
    @Override public float lads$raise() { return lads$raise; }
    @Override public void lads$posed(float yaw, float pitch, float raise) { lads$yaw = yaw; lads$pitch = pitch; lads$raise = raise; }
}
