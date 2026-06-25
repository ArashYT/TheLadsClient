package com.thelads.core.mixin.auto.safemod2;

import net.minecraft.client.renderer.texture.TextureTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextureTransform.class)
public class MixinTextureTransform {
    @Inject(method = "apply", at = @At("HEAD"), require = 0)
    private void onApply(net.minecraft.client.renderer.texture.TextureAtlasSprite sprite, net.minecraft.util.Mth.PoseStack matrix) {
        // Custom logic here
    }
}
