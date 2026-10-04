package com.thelads.core.v26_2.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.thelads.core.v26_2.feature.NativeDynamicLights;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Dynamic Lights in the world's light: vanilla's and Sodium's chunk meshing, block entities and fluids all ask this. */
@Mixin(LightCoordsUtil.class)
abstract class DynamicLightsBlockMixin {
    @ModifyReturnValue(method = "getLightCoords(Lnet/minecraft/util/LightCoordsUtil$BrightnessGetter;Lnet/minecraft/world/level/BlockAndLightGetter;"
        + "Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I", at = @At("RETURN"), require = 1)
    private static int ladsDynamicLight(int coords, @Local(argsOnly = true) BlockPos pos) {
        return NativeDynamicLights.coords(coords, pos);
    }
}
