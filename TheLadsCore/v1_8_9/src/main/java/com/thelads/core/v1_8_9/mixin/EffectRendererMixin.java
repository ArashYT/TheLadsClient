package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.Particles189;
import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Particles189: particles outside the view are not drawn (the frame's own frustum, so nothing on screen changes), and the
 * ParticleBudget module limits decorative particles. OptiFine patches this class: require = 0 leaves vanilla if one moves.
 */
@Mixin(EffectRenderer.class)
public abstract class EffectRendererMixin {
    @Inject(method = "renderParticles", at = @At("HEAD"), require = 0)
    private void ladsFrustum(Entity entity, float partialTicks, CallbackInfo ci) {
        Particles189.beginFrame();
    }

    @Redirect(method = "renderParticles", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/EntityFX;renderParticle(Lnet/minecraft/client/renderer/WorldRenderer;Lnet/minecraft/entity/Entity;FFFFFF)V"), require = 0)
    private void ladsDraw(EntityFX particle, WorldRenderer buffer, Entity entity, float partialTicks, float rx, float rz, float ryz, float rxy, float rxz) {
        if (Particles189.inView(particle, partialTicks)) particle.renderParticle(buffer, entity, partialTicks, rx, rz, ryz, rxy, rxz);
    }

    @Inject(method = "spawnEffectParticle", at = @At("HEAD"), cancellable = true, require = 0)
    private void ladsBudget(int id, double x, double y, double z, double dx, double dy, double dz, int[] parameters, CallbackInfoReturnable<EntityFX> cir) {
        if (!Particles189.allow(id, x, y, z)) cir.setReturnValue(null);
    }
}
