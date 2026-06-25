package com.thelads.core.mixin.auto.kerria1301211fabric;

import net.minecraft.client.renderer.texture.NativeImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NativeImage.class)
public class NativeImageMixin {

    @Inject(method = "upload", at = @At("HEAD"), require = 0)
    private void onUpload(CallbackInfo ci) {
        // Custom logic here
    }
}
