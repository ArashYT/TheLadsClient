package com.thelads.core.mixin.auto.safemod2;

import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {
    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void onRender(float partialTicks, long nanoTime) {
        // Custom logic here
    }
}
