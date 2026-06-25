package com.thelads.core.mixin.auto.kerria1301211fabric;

import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightTexture.class)
public class LightTextureMixin {

    @Inject(method = "update", at = @At("HEAD"), require = 0)
    private void onUpdate(CallbackInfo ci) {
        // Custom logic here
    }
}
