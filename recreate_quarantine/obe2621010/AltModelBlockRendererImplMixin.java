package com.thelads.core.mixin.auto.obe2621010;

import net.minecraft.client.renderer.block.AltModelBlockRendererImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(net.minecraft.client.renderer.block.AltModelBlockRendererImpl.class)
public class AltModelBlockRendererImplMixin {

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void render(AltModelBlockRendererImpl altModelBlockRendererImpl, CallbackInfo ci) {
        // Custom logic here
    }
}
