package com.thelads.core.mixin;

import net.minecraft.client.Camera;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    private Level level;

    @Shadow
    @Final
    private net.minecraft.client.Minecraft minecraft;

    @Inject(method = "getCameraEntityPartialTicks", at = @At("HEAD"), cancellable = true)
    private void ladsSafePartialTicks(CallbackInfoReturnable<Float> cir) {
        // If the game level isn't loaded yet (e.g., joining a server transition),
        // we cannot calculate partial ticks because it relies on the level's tick manager.
        if (this.level == null) {
            cir.setReturnValue(1.0F);
        }
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void ladsSafeExtractRenderState(net.minecraft.client.renderer.state.level.CameraRenderState state, float tickDelta, CallbackInfo ci) {
        if (this.minecraft.player == null) {
            ci.cancel();
        }
    }
}
