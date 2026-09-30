package com.thelads.core.v26_2.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.thelads.core.v26_2.feature.NativeRenderScale;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.TextureFilteringMethod;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.GlobalSettingsUniform;
import net.minecraft.client.renderer.state.GameRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class RenderScaleMixin {
    @Shadow @Final @Mutable private RenderTarget mainRenderTarget;
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private GameRenderState gameRenderState;
    @Shadow @Final private GlobalSettingsUniform globalSettingsUniform;
    @Unique private final NativeRenderScale lads$renderScale = new NativeRenderScale();
    @Unique private RenderTarget lads$nativeTarget;

    @WrapMethod(method = "render")
    private void lads$renderFrame(DeltaTracker delta, boolean renderLevel, Operation<Void> original) {
        boolean world = renderLevel && minecraft.level != null && minecraft.isGameLoadFinished();
        boolean succeeded = false;
        try {
            lads$renderScale.beginFrame(world, mainRenderTarget);
            original.call(delta, renderLevel);
            succeeded = true;
        } finally {
            // Also runs if a renderer or another mod throws during the world pass.
            // An aborted renderer only needs its original Java target restored; avoid
            // secondary GPU/uniform work masking the renderer's original exception.
            lads$restoreNativeTarget(succeeded ? delta : null);
            lads$renderScale.endFrame(mainRenderTarget, world, succeeded);
            if (succeeded) com.thelads.core.v26_2.feature.NativeWelcomeProbe.renderedFrame(mainRenderTarget);
            if (succeeded) com.thelads.core.v26_2.feature.NativeWorldVerification.renderedFrame(mainRenderTarget);
        }
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;)V"), require = 1)
    private void lads$beginWorld(DeltaTracker delta, boolean renderLevel, CallbackInfo ci) {
        RenderTarget scaled = lads$renderScale.beginWorld(mainRenderTarget);
        if (scaled != mainRenderTarget) {
            lads$nativeTarget = mainRenderTarget;
            mainRenderTarget = scaled;
            lads$updateScreenUniform(delta);
        }
    }

    // World render, entity outlines and spectator post processing have all finished.
    // The next vanilla operation clears depth for the GUI on the original target.
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/fog/FogRenderer;endFrame()V"), require = 1)
    private void lads$finishWorld(DeltaTracker delta, boolean renderLevel, CallbackInfo ci) {
        if (lads$nativeTarget != null) {
            RenderTarget destination = lads$nativeTarget;
            lads$restoreNativeTarget(delta);
            lads$renderScale.composite(destination);
        }
    }

    @Unique private void lads$restoreNativeTarget(DeltaTracker delta) {
        if (lads$nativeTarget == null) return;
        mainRenderTarget = lads$nativeTarget;
        lads$nativeTarget = null;
        if (delta != null) lads$updateScreenUniform(delta);
    }

    @Unique private void lads$updateScreenUniform(DeltaTracker delta) {
        var options = gameRenderState.optionsRenderState;
        globalSettingsUniform.update(mainRenderTarget.width, mainRenderTarget.height, options.glintStrength,
            minecraft.level == null ? 0 : minecraft.level.getGameTime(), delta, options.menuBackgroundBlurriness,
            gameRenderState.levelRenderState.cameraRenderState.pos, options.textureFiltering == TextureFilteringMethod.RGSS);
    }

    @Inject(method = "resize", at = @At("HEAD"), require = 1)
    private void lads$releaseOnResize(int width, int height, CallbackInfo ci) { lads$renderScale.onVanillaResizeOrClose(); }

    @Inject(method = "close", at = @At("HEAD"), require = 1)
    private void lads$releaseOnClose(CallbackInfo ci) { lads$renderScale.onVanillaResizeOrClose(); }
}
