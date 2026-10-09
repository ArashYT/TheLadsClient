package com.thelads.core.v26_2.mixin.particlecore;

import com.thelads.core.client.ParticleCore;
import net.minecraft.client.particle.QuadParticleGroup;
import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 26.3 already leaves out the flat particles whose centre is outside the view; Particle Core's Culling switch owns that test.
 * It counts what is drawn and what is culled (the QA numbers), and with Culling off every particle is drawn.
 */
@Mixin(QuadParticleGroup.class)
public abstract class QuadParticleGroupMixin {
    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/culling/Frustum;pointInFrustum(DDD)Z"))
    private boolean ladsCull(Frustum frustum, double x, double y, double z) {
        if (!ParticleCore.active()) return frustum.pointInFrustum(x, y, z);
        if (!ParticleCore.culling() || frustum.pointInFrustum(x, y, z)) { ParticleCore.drawn++; return true; }
        ParticleCore.culled++;
        return false;
    }
}
