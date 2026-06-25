package com.thelads.core.mixin.auto.safemod2;

import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightTexture.class)
public class MixinLightTexture {
    @Inject(method = "update", at = @At("HEAD"), require = 0)
    private void onUpdate() {
        // Custom logic here
    }
}
