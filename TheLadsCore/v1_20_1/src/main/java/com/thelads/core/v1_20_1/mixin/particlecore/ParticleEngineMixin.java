package com.thelads.core.v1_20_1.mixin.particlecore;

import com.thelads.core.client.ParticleCore;
import com.thelads.core.v1_20_1.feature.ParticleCore1201;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Spawn side of Particle Core: per-type rates, potion swirls and the decorative quota decide before a particle exists. */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
    @Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
    private void ladsSpawn(ParticleOptions options, double x, double y, double z, double dx, double dy, double dz, CallbackInfoReturnable<Particle> cir) {
        if (ParticleCore.active() && !ParticleCore1201.allow(options, x, y, z)) cir.setReturnValue(null);
    }
}
