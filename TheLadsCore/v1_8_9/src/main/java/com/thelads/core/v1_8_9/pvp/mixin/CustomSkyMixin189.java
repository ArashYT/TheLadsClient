package com.thelads.core.v1_8_9.pvp.mixin;

import com.thelads.core.v1_8_9.pvp.CustomSky189;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Custom Skies: drawn where vanilla has turned the sky -90 degrees about Y and is about to turn it with the celestial angle for the
 * sun (the second getCelestialAngle; the first picks the sunrise colour). require 0: OptiFine rewrites renderSky, and draws these
 * skies itself there.
 */
@Mixin(RenderGlobal.class)
public abstract class CustomSkyMixin189 {
    @Shadow @Final private Minecraft mc;
    @Shadow private WorldClient theWorld;

    @Inject(method = "renderSky(FI)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/WorldClient;getCelestialAngle(F)F", ordinal = 1), require = 0)
    private void lads$customSky(float partialTicks, int pass, CallbackInfo ci) {
        if (theWorld != null) CustomSky189.render(mc, theWorld, partialTicks);
    }
}
