package com.thelads.core.v26_2.mixin.pvp;

import com.thelads.core.client.PvpVisuals;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Render Tweaks, Hide Elder Guardian Curse: the guardian faces are not submitted (Mining Fatigue and its sound stay). */
@Mixin(targets = "net.minecraft.client.particle.ElderGuardianParticleGroup$State")
public class ElderGuardianParticleStateMixin26 {
    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private void lads$hideCurse(SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        if (PvpVisuals.hideElderGuardian) ci.cancel();
    }
}
