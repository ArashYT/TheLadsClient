package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.cartcam.MinecartCameraRotation;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class CartCamGameRendererMixin {
    @Inject(method = "update(Lnet/minecraft/client/DeltaTracker;)V", at = @At("HEAD"))
    private void lads$onCartCamRenderFrame(DeltaTracker deltaTracker, CallbackInfo ci) {
        MinecartCameraRotation.onRenderFrame(deltaTracker);
    }
}
