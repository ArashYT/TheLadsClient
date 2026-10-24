package com.thelads.core.v1_8_9.mixin.pvp;

import com.thelads.core.client.PvpVisuals;
import net.minecraft.client.particle.MobAppearance;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Render Tweaks, Hide Elder Guardian Curse: the guardian face over the screen is not drawn (Mining Fatigue and its sound stay). */
@Mixin(MobAppearance.class)
public abstract class MobAppearanceMixin189 {
    @Inject(method = "renderParticle", at = @At("HEAD"), cancellable = true)
    private void ladsHideCurse(WorldRenderer buffer, Entity view, float partialTicks, float rx, float rz, float ryz, float rxy, float rxz, CallbackInfo ci) {
        if (PvpVisuals.hideElderGuardian) ci.cancel();
    }
}
