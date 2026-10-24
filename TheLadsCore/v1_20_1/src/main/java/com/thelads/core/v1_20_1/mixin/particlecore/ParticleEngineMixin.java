package com.thelads.core.v1_20_1.mixin.particlecore;

import com.thelads.core.client.ParticleCore;
import com.thelads.core.v1_20_1.feature.ParticleCore1201;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.particles.ParticleOptions;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.particle.ParticleRenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Spawn side of Particle Core: per-type rates, potion swirls and the decorative quota decide before a particle exists. */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
    @Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
    private void ladsSpawn(ParticleOptions options, double x, double y, double z, double dx, double dy, double dz, CallbackInfoReturnable<Particle> cir) {
        if (ParticleCore.active() && !ParticleCore1201.allow(options, x, y, z)) cir.setReturnValue(null);
    }

    /** Particle Core's per-type size, color and fade: the new particle keeps its type's look (ParticleStyleMixin1201 draws it). */
    @Inject(method = "createParticle", at = @At("RETURN"))
    private void ladsStyle(ParticleOptions options, double x, double y, double z, double dx, double dy, double dz, CallbackInfoReturnable<Particle> cir) {
        if (ParticleCore.styled() && cir.getReturnValue() != null) ParticleCore.style(cir.getReturnValue(), ParticleCore1201.id(options.getType()));
    }

    /** A fading particle of the opaque sheet goes to the translucent one (same atlas), so its alpha shows instead of cutting off. */
    // require = 0: a modpack mod that wraps this same call (Zero SMP, the SMP pack) leaves the hook out instead of crashing the game.
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/Particle;getRenderType()Lnet/minecraft/client/particle/ParticleRenderType;"), require = 0)
    private ParticleRenderType ladsFadeSheet(Particle particle, Operation<ParticleRenderType> original) {
        ParticleRenderType type = original.call(particle);
        return type == ParticleRenderType.PARTICLE_SHEET_OPAQUE && particle instanceof ParticleCore1201.Look look && look.lads$fades() ? ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT : type;
    }

    /** A styled particle ticks with its own size, color and alpha (the look is swapped in only while it draws). */
    @Inject(method = "tickParticle", at = @At("HEAD"))
    private void ladsUnstyle(Particle particle, CallbackInfo ci) {
        if (particle instanceof ParticleCore1201.Look look) look.lads$end();
    }

    /**
     * QA timing of the game's particle drawing (a no-op unless ParticleCore.timing). Forge adds its own overload of render (with a
     * frustum), which LevelRenderer calls instead under Forge: there this hook does not fire (require = 0) and the QA reports no time.
     */
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;F)V", at = @At("HEAD"), require = 0)
    private void ladsRenderBegin(PoseStack poseStack, MultiBufferSource.BufferSource buffers, LightTexture light, Camera camera, float partialTick, CallbackInfo ci) { ParticleCore.timeBegin(); }

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;F)V", at = @At("RETURN"), require = 0)
    private void ladsRenderEnd(PoseStack poseStack, MultiBufferSource.BufferSource buffers, LightTexture light, Camera camera, float partialTick, CallbackInfo ci) { ParticleCore.timeEnd(); }
}
