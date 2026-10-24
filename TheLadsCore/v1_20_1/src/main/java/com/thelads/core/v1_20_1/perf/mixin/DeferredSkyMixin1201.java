package com.thelads.core.v1_20_1.perf.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.perf.DeferredSky;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Deferred sky rendering on 1.20.1 (DeferredSky, Polydium parity).
 * Ensures early-Z depth testing (GL_LEQUAL) is preserved during sky disc and celestial rendering,
 * discarding occluded sky pixels behind solid terrain and saving shader fillrate.
 */
@Mixin(LevelRenderer.class)
public abstract class DeferredSkyMixin1201 {
    @Inject(method = "renderSky(Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;FLnet/minecraft/client/Camera;ZLjava/lang/Runnable;)V", at = @At("HEAD"))
    private void lads$beforeSky(PoseStack poseStack, Matrix4f projectionMatrix, float tickDelta, Camera camera, boolean isFoggy, Runnable skyFogSetup, CallbackInfo ci) {
        if (!DeferredSky.isEnabled()) return;
        DeferredSky.setActive(true);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
    }

    @Inject(method = "renderSky(Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;FLnet/minecraft/client/Camera;ZLjava/lang/Runnable;)V", at = @At("RETURN"))
    private void lads$afterSky(PoseStack poseStack, Matrix4f projectionMatrix, float tickDelta, Camera camera, boolean isFoggy, Runnable skyFogSetup, CallbackInfo ci) {
        if (!DeferredSky.isEnabled()) return;
        DeferredSky.setActive(false);
        RenderSystem.depthMask(true);
    }
}
