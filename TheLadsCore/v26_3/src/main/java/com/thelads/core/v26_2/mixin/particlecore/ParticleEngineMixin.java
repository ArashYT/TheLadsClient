package com.thelads.core.v26_2.mixin.particlecore;

import com.thelads.core.client.ParticleCore;
import com.thelads.core.v26_2.feature.NativeParticleCore;
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
    private void ladsSpawn(ParticleOptions options, double x, double y, double z, double xa, double ya, double za, CallbackInfoReturnable<Particle> cir) {
        if (ParticleCore.active() && !NativeParticleCore.allow(options, x, y, z)) cir.setReturnValue(null);
    }
}
