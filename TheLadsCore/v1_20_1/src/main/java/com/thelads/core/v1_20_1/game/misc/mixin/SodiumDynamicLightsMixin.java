package com.thelads.core.v1_20_1.game.misc.mixin;

import com.thelads.core.v1_20_1.game.misc.NativeDynamicLights1201;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Dynamic Lights in Sodium 0.5.x and Embeddium 0.3.x (which keeps Sodium's package on 1.20.1): their chunk meshers read light from
 * LightDataAccess, whose compute(x, y, z) packs block light in bits 0-3, never asking vanilla's LevelRenderer.getLightColor. Smooth
 * and flat light pipelines, fluids and the ambient-occlusion samples all go through this cache, so a brighter dynamic light is
 * folded in here. Absent from a game without either mod: require 0.
 */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.model.light.data.LightDataAccess", remap = false)
public abstract class SodiumDynamicLightsMixin {
    @Inject(method = "compute(III)I", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void lads$dynamicLight(int x, int y, int z, CallbackInfoReturnable<Integer> callback) {
        int word = callback.getReturnValueI(), lit = NativeDynamicLights1201.sodiumWord(word, x, y, z);
        if (lit != word) callback.setReturnValue(lit);
    }
}
