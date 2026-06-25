package com.thelads.core.mixin.auto.resourcify262fabric184;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraft {

    @Inject(method = "init", at = @At("HEAD"), require = 0)
    private void onInit(CallbackInfo ci) {
        // Minimal safe injection point
    }
}
