package com.thelads.core.mixin.auto.safemod2;

import net.minecraft.client.renderer.texture.TextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextureManager.class)
public class MixinTextureManager {
    @Inject(method = "bind", at = @At("HEAD"), require = 0)
    private void onBind(net.minecraft.resources.ResourceLocation location) {
        // Custom logic here
    }
}
