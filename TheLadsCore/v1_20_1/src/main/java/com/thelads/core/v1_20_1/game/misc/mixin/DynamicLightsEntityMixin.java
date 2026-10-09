package com.thelads.core.v1_20_1.game.misc.mixin;

import com.thelads.core.v1_20_1.game.misc.NativeDynamicLights1201;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Dynamic Lights on entities, their leashes and the first-person hand (all read the entity renderer's block light). */
@Mixin(EntityRenderer.class)
abstract class DynamicLightsEntityMixin {
    @Inject(method = "getBlockLightLevel", at = @At("RETURN"), cancellable = true, require = 1)
    private void lads$dynamicLight(Entity entity, BlockPos pos, CallbackInfoReturnable<Integer> callback) {
        int light = callback.getReturnValueI(), lit = NativeDynamicLights1201.blockLight(light, pos);
        if (lit != light) callback.setReturnValue(lit);
    }
}
