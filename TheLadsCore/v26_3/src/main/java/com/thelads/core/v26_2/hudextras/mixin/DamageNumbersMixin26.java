package com.thelads.core.v26_2.hudextras.mixin;

import com.thelads.core.v26_2.hudextras.HudExtras26;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Damage Indicator's floating numbers: text gizmos emitted with the debug renderer's, which runs every frame with F3 or without. */
@Mixin(DebugRenderer.class)
public class DamageNumbersMixin26 {
    @Inject(method = "emitGizmos", at = @At("TAIL"))
    private void lads$damageNumbers(Frustum frustum, double camX, double camY, double camZ, float partial, CallbackInfo ci) {
        HudExtras26.numbers();
    }
}
