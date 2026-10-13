package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.OptiFineShaderLink189;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks OptiFine's Shaders.setShaderPack to automatically turn Fast Render OFF when a shader pack is turned ON,
 * and restore it when shaders are switched OFF.
 */
@Pseudo
@Mixin(targets = "net.optifine.shaders.Shaders", remap = false)
public abstract class OptiFineShadersMixin {
    @Inject(method = "setShaderPack", at = @At("HEAD"), require = 0, remap = false)
    private static void ladsOnSetShaderPack(String shaderPack, CallbackInfo ci) {
        OptiFineShaderLink189.onSetShaderPack(shaderPack);
    }
}
