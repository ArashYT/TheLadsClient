package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.NativeClientTools;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleEngine.class)
public abstract class ParticleBudgetMixin {
    @Inject(method = "createParticle", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsBudget(ParticleOptions type, double x, double y, double z, double dx, double dy, double dz,
                            CallbackInfoReturnable<Particle> cir) {
        if (!NativeClientTools.allowParticle(type, x, y, z)) cir.setReturnValue(null);
    }
}
