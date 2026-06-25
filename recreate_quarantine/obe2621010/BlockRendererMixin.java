package com.thelads.core.mixin.auto.obe2621010;

import net.minecraft.client.renderer.block.BlockRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(net.minecraft.client.renderer.block.BlockRenderer.class)
public class BlockRendererMixin {

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void render(BlockRenderer blockRenderer, CallbackInfo ci) {
        // Custom logic here
    }
}
