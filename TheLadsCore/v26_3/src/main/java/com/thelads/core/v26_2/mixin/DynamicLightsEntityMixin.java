package com.thelads.core.v26_2.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.thelads.core.v26_2.feature.NativeDynamicLights;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Dynamic Lights on entities, their leashes and the first-person hand (all read the entity renderer's block light). */
@Mixin(EntityRenderer.class)
abstract class DynamicLightsEntityMixin {
    @ModifyReturnValue(method = "getBlockLightLevel", at = @At("RETURN"), require = 1)
    private int ladsDynamicLight(int light, @Local(argsOnly = true) BlockPos pos) {
        return NativeDynamicLights.blockLight(light, pos);
    }
}
