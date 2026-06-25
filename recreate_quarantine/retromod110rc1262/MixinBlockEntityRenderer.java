package com.thelads.core.mixin.auto.retromod110rc1262;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntityRenderer.class)
public class MixinBlockEntityRenderer {

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void render(CallbackInfo ci) {
        // Minimal safe injection
    }
}
