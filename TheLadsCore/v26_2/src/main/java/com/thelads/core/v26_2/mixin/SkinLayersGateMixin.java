package com.thelads.core.v26_2.mixin;

import com.thelads.core.modules.SkinLayersModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The SkinLayers module switches the bundled 3D Skin Layers on players. With no 3D mesh a player keeps vanilla's flat layers:
 * the mod clears every player's meshes before each setup, so the change shows on the next frame. Heads follow the mod's own setting.
 */
@Pseudo
@Mixin(targets = "dev.tr7zw.skinlayers.SkinUtil", remap = false)
public abstract class SkinLayersGateMixin {
    @Inject(method = "setup3dLayers(Lnet/minecraft/world/entity/Avatar;Ldev/tr7zw/skinlayers/accessor/PlayerSettings;Z)Z",
            at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void ladsSkinLayersModule(CallbackInfoReturnable<Boolean> cir) {
        if (!SkinLayersModule.layersOn()) cir.setReturnValue(false);
    }
}
