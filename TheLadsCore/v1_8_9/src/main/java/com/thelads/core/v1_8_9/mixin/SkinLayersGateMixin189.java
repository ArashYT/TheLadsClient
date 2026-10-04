package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.modules.SkinLayersModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The SkinLayers module switches the bundled 3D Skin Layers on players: 1.8.9's build makes 3D layers only for a player whose skin
 * is not a default one, so with the module off none are made. SkinLayers189 drops the ones already made; RenderPlayerMixin
 * shows vanilla's flat layers again.
 */
@Pseudo
@Mixin(targets = "dev.tr7zw.skinlayers.SkinUtil", remap = false)
public abstract class SkinLayersGateMixin189 {
    @Inject(method = "hasCustomSkin", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void ladsSkinLayersModule(CallbackInfoReturnable<Boolean> cir) {
        if (!SkinLayersModule.layersOn()) cir.setReturnValue(false);
    }
}
