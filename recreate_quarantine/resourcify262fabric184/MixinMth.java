package com.thelads.core.mixin.auto.resourcify262fabric184;

import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mth.class)
public class MixinMth {

    @Inject(method = "clamp", at = @At("HEAD"), require = 0)
    private static void onClamp(float value, float min, float max, CallbackInfo ci) {
        // Minimal safe injection point
    }
}
