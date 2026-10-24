package com.thelads.core.v1_8_9.world.mixin;

import com.thelads.core.v1_8_9.perf.StarlightLightingEngine189;
import net.minecraft.util.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * World lighting optimization mixin: delegates checkLightFor calls to the Starlight-class
 * asynchronous lighting engine when enabled, eliminating render thread light update stalls.
 */
@Mixin(World.class)
public abstract class WorldLightingMixin189 {
    @Inject(method = "checkLightFor", at = @At("HEAD"), cancellable = true, require = 0)
    private void lads$starlightCheckLight(EnumSkyBlock lightType, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (StarlightLightingEngine189.onCheckLightFor((World) (Object) this, lightType, pos)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "tick", at = @At("TAIL"), require = 0)
    private void lads$starlightProcessBatch(CallbackInfo ci) {
        StarlightLightingEngine189.processBatch((World) (Object) this, 128);
    }
}
