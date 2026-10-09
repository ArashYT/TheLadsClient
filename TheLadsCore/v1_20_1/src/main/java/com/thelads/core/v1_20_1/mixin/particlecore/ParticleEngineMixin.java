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

    /**
     * QA timing of the game's particle drawing (a no-op unless ParticleCore.timing). Forge adds its own overload of render (with a
     * frustum), which LevelRenderer calls instead under Forge: there this hook does not fire (require = 0) and the QA reports no time.
     */
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;F)V", at = @At("HEAD"), require = 0)
    private void ladsRenderBegin(PoseStack poseStack, MultiBufferSource.BufferSource buffers, LightTexture light, Camera camera, float partialTick, CallbackInfo ci) { ParticleCore.timeBegin(); }

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;F)V", at = @At("RETURN"), require = 0)
    private void ladsRenderEnd(PoseStack poseStack, MultiBufferSource.BufferSource buffers, LightTexture light, Camera camera, float partialTick, CallbackInfo ci) { ParticleCore.timeEnd(); }
}
