package com.thelads.core.v1_20_1.lod.mixin;

import com.thelads.core.perf.PlayerLod;
import com.thelads.core.v1_20_1.lod.PlayerLod1201;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Player LOD: armor trims beyond the trim distance (the armor itself stays). */
@Mixin(HumanoidArmorLayer.class)
abstract class PlayerLodTrimMixin {
    @Inject(method = "renderTrim", at = @At("HEAD"), cancellable = true)
    private void lads$lodTrim(CallbackInfo ci) {
        if (PlayerLod1201.has(PlayerLod.TRIM)) ci.cancel();
    }
}
