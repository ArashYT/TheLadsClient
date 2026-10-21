package com.thelads.core.v1_8_9.mixin.optifine;

import com.thelads.core.modules.OptiFine189Module;
import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.client.particle.EntityFX;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * OptiFine Animations particle gating: suppresses suppressed particle types at spawn time
 * with zero per-frame allocation and fast integer switch.
 */
@Mixin(EffectRenderer.class)
public abstract class EffectRendererOfMixin189 {
    @Inject(method = "spawnEffectParticle", at = @At("HEAD"), cancellable = true)
    private void lads$ofParticleFilter(int id, double x, double y, double z,
            double dx, double dy, double dz, int[] params, CallbackInfoReturnable<EntityFX> cir) {
        if (!OptiFine189Module.allowParticle(id)) {
            cir.setReturnValue(null);
        }
    }
}
