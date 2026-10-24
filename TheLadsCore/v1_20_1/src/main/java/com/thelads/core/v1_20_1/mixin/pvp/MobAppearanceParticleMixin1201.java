package com.thelads.core.v1_20_1.mixin.pvp;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thelads.core.client.PvpVisuals;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.MobAppearanceParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Render Tweaks, Hide Elder Guardian Curse: the guardian face over the screen is not drawn (Mining Fatigue and its sound stay). */
@Mixin(MobAppearanceParticle.class)
public class MobAppearanceParticleMixin1201 {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void lads$hideCurse(VertexConsumer buffer, Camera camera, float partialTick, CallbackInfo ci) {
        if (PvpVisuals.hideElderGuardian) ci.cancel();
    }
}
