package com.thelads.core.v26_2.mixin.particlecore;

import com.thelads.core.client.ParticleCore;
import com.thelads.core.v26_2.feature.NativeParticleCore;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.ParticlesRenderState;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Spawn side of Particle Core: per-type rates, potion swirls and the decorative quota decide before a particle exists. */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
    @Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
    private void ladsSpawn(ParticleOptions options, double x, double y, double z, double xa, double ya, double za, CallbackInfoReturnable<Particle> cir) {
        if (ParticleCore.active() && !NativeParticleCore.allow(options, x, y, z)) cir.setReturnValue(null);
    }

    /** QA timing of the particle extraction (a no-op unless ParticleCore.timing). */
    @Inject(method = "extract", at = @At("HEAD"))
    private void ladsExtractBegin(ParticlesRenderState state, Frustum frustum, Camera camera, float partialTick, CallbackInfo ci) { ParticleCore.timeBegin(); }

    @Inject(method = "extract", at = @At("RETURN"))
    private void ladsExtractEnd(ParticlesRenderState state, Frustum frustum, Camera camera, float partialTick, CallbackInfo ci) { ParticleCore.timeEnd(); }
}
