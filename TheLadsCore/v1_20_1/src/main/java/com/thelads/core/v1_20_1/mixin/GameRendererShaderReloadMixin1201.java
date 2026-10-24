package com.thelads.core.v1_20_1.mixin;

import com.thelads.core.client.pack.PackAssetMask;
import java.util.Map;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skips recompiling all core GLSL shaders in {@code GameRenderer.reloadShaders} during a
 * resource-pack switch when none of the added, removed, or reordered packs contain shaders.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererShaderReloadMixin1201 {
    @Shadow @Final private Map<String, ShaderInstance> shaders;

    @Inject(method = "reloadShaders", at = @At("HEAD"), cancellable = true)
    private void ladsSkipUnchangedShaderReload(ResourceProvider resourceProvider, CallbackInfo ci) {
        if (this.shaders != null && !this.shaders.isEmpty() && PackAssetMask.shouldSkipShaders()) {
            ci.cancel();
        }
    }

    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true, require = 0)
    private void ladsBobDisconnect(com.mojang.blaze3d.vertex.PoseStack matrices, float tickDelta, CallbackInfo ci) {
        if (com.thelads.core.client.BobbingTweaks.isDisconnectEnabled()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderItemActivationAnimation", at = @At("HEAD"), cancellable = true, require = 0)
    private void ladsTotemAnimation(int width, int height, float tickDelta, CallbackInfo ci) {
        if (com.thelads.core.client.TotemAnimation.shouldHideAnimation()) {
            ci.cancel();
        }
    }
}
