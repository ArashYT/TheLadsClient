package com.thelads.core.mixin.auto.safemod2;

import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OverlayTexture.class)
public class MixinOverlayTexture {
    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void onRender(net.minecraft.client.renderer.MultiBufferSource.BufferSource buffer, int packedLight) {
        // Custom logic here
    }
}
