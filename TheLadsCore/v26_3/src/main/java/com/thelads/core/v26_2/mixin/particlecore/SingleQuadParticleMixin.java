package com.thelads.core.v26_2.mixin.particlecore;

import com.thelads.core.client.ParticleCore;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optimized rendering, extract side: the rotation every flat particle is drawn with is one reused Quaternionf, not a new one each frame. */
@Mixin(SingleQuadParticle.class)
public abstract class SingleQuadParticleMixin extends Particle {
    private static final Quaternionf LADS_ROTATION = new Quaternionf();

    protected SingleQuadParticleMixin(ClientLevel level, double x, double y, double z) { super(level, x, y, z); }

    @Shadow protected float roll;
    @Shadow protected float oRoll;
    @Shadow public abstract SingleQuadParticle.FacingCameraMode getFacingCameraMode();
    @Shadow protected abstract void extractRotatedQuad(QuadParticleRenderState state, Camera camera, Quaternionf rotation, float partialTick);

    @Inject(method = "extract", at = @At("HEAD"), cancellable = true)
    private void ladsExtract(QuadParticleRenderState state, Camera camera, float partialTick, CallbackInfo ci) {
        if (!ParticleCore.optimized()) return;
        this.getFacingCameraMode().setRotation(LADS_ROTATION, camera, partialTick);
        if (this.roll != 0.0F) LADS_ROTATION.rotateZ(Mth.lerp(partialTick, this.oRoll, this.roll));
        this.extractRotatedQuad(state, camera, LADS_ROTATION, partialTick);
        ci.cancel();
    }
}
