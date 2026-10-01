package com.thelads.core.v1_21_11.feature.qa.mixin;

import com.thelads.core.v1_21_11.feature.NativeWorldVerification;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The completed frame (world, GUI and screen) for auto-world QA captures; returns at once outside a verified QA run. */
@Mixin(GameRenderer.class)
public class GameRendererQaMixin {
    @Inject(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V", at = @At("TAIL"), require = 1)
    private void ladsQaFrame(DeltaTracker delta, boolean renderLevel, CallbackInfo ci) {
        NativeWorldVerification.renderedFrame(Minecraft.getInstance().getMainRenderTarget());
        com.thelads.core.v1_21_11.feature.qa.LoadingScreenCapture.frame(Minecraft.getInstance().getMainRenderTarget());
    }
}
