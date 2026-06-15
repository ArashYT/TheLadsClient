package com.thelads.core.client.renderscale;

import com.mojang.blaze3d.pipeline.MainTarget;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;

public class RenderScaleManager {
    private static boolean redirect = false;
    private static RenderTarget scaledRenderTarget = null;
    private static long lastFrameTime = 0L;
    private static float smoothedFps = 0.0f;
    private static float activeScale = 1.0f;

    public static float getActiveScale() {
        return RenderScaleOptions.isDynamicResolution() ? activeScale : RenderScaleOptions.getRenderScale();
    }

    public static boolean shouldRedirectTarget() {
        return redirect && RenderScaleManager.getActiveScale() != 1.0f && scaledRenderTarget != null;
    }

    public static RenderTarget getScaledRenderTarget() {
        return scaledRenderTarget;
    }

    public static void beginRedirect() {
        float scale;
        double deltaSeconds;
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return;
        }
        RenderTarget main = client.getMainRenderTarget();
        if (main == null) {
            return;
        }
        long now = System.nanoTime();
        if (lastFrameTime != 0L && (deltaSeconds = (double)(now - lastFrameTime) / 1.0E9) > 0.0 && deltaSeconds < 1.0) {
            float currentFps = (float)(1.0 / deltaSeconds);
            smoothedFps = smoothedFps == 0.0f ? currentFps : smoothedFps * 0.9f + currentFps * 0.1f;
            if (RenderScaleOptions.isDynamicResolution()) {
                float maxScale = RenderScaleOptions.getRenderScale();
                float minScale = RenderScaleOptions.getMinRenderScale();
                int targetFps = RenderScaleOptions.getTargetFps();
                float adjustmentSpeed = 0.2f * (float)deltaSeconds;
                if (smoothedFps < (float)targetFps - 3.0f) {
                    activeScale -= adjustmentSpeed;
                } else if (smoothedFps > (float)targetFps + 3.0f) {
                    activeScale += adjustmentSpeed * 0.5f;
                }
                float lowerBound = Math.min(minScale, maxScale);
                activeScale = Math.max(lowerBound, Math.min(maxScale, activeScale));
            }
        }
        lastFrameTime = now;
        if (!RenderScaleOptions.isDynamicResolution()) {
            activeScale = RenderScaleOptions.getRenderScale();
        }
        if ((scale = RenderScaleManager.getActiveScale()) == 1.0f) {
            redirect = false;
            return;
        }
        int scaledW = Math.max(1, Math.round((float)main.width * scale));
        int scaledH = Math.max(1, Math.round((float)main.height * scale));
        if (scaledRenderTarget == null) {
            scaledRenderTarget = new MainTarget(scaledW, scaledH);
        } else if (RenderScaleManager.scaledRenderTarget.width != scaledW || RenderScaleManager.scaledRenderTarget.height != scaledH) {
            scaledRenderTarget.resize(scaledW, scaledH);
        }
        redirect = true;
    }

    public static void endRedirect() {
        if (!redirect) {
            return;
        }
        redirect = false;
        float scale = RenderScaleManager.getActiveScale();
        if (scale == 1.0f || scaledRenderTarget == null) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return;
        }
        RenderTarget main = client.getMainRenderTarget();
        if (main == null) {
            return;
        }
        FilterMode mode = RenderScaleOptions.getScaleAlgorithm() == ScaleAlgorithm.LINEAR ? FilterMode.LINEAR : FilterMode.NEAREST;
        RenderScaleManager.customBlit(scaledRenderTarget, main, mode);
    }

    public static void customBlit(RenderTarget src, RenderTarget dest, FilterMode filterMode) {
        RenderSystem.assertOnRenderThread();
        try (RenderPass renderpass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Blit render target", dest.getColorTextureView(), OptionalInt.empty());){
            renderpass.setPipeline(RenderPipelines.TRACY_BLIT);
            RenderSystem.bindDefaultUniforms((RenderPass)renderpass);
            renderpass.bindTexture("InSampler", src.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(filterMode));
            renderpass.draw(0, 3);
        }
    }

    public static void onMainTargetResized(int width, int height) {
    }
}
