package com.thelads.core.v26_2.mixin.particlecore;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.client.ParticleCore;
import net.minecraft.client.particle.QuadParticleGroup;
import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 26.3 already leaves out the flat particles whose centre is outside the view; Particle Core's Culling switch owns that test.
 * It counts what is drawn and what is culled (the QA numbers, also with the module off, which is plain vanilla), and with
 * Culling off every particle is drawn. A wrap, not a redirect, so another mod's change to the same call still chains.
 */
@Mixin(QuadParticleGroup.class)
public abstract class QuadParticleGroupMixin {
    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/culling/Frustum;pointInFrustum(DDD)Z"))
    private boolean ladsCull(Frustum frustum, double x, double y, double z, Operation<Boolean> original) {
        // Off: draw everything, but only while we are the ones deciding (active); otherwise this is vanilla's own test.
        boolean visible = original.call(frustum, x, y, z) || ParticleCore.active() && !ParticleCore.culling();
        if (visible) ParticleCore.drawn++; else ParticleCore.culled++;
        return visible;
    }
}
