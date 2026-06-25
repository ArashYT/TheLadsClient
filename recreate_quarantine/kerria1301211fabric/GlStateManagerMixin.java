package com.thelads.core.mixin.auto.kerria1301211fabric;

import net.minecraft.client.renderer.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GlStateManager.class)
public class GlStateManagerMixin {

    @Inject(method = "pushMatrix", at = @At("HEAD"), require = 0)
    private void onPushMatrix(CallbackInfo ci) {
        // Custom logic here
    }
}
