package com.thelads.core.v26_2.feature;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import com.thelads.core.v26_2.mixin.RenderScaleTargetsAccessorMixin;

/** A render-thread-owned world attachment. The vanilla presentation/GUI target is never resized here. */
public final class NativeRenderScale {
    private final RenderScalePolicy policy = new RenderScalePolicy();
    private RenderTarget world;
    private RenderTarget scaledOutline;
    private RenderScalePolicy.Settings settings;
    private boolean usedThisFrame;
    private int nativeWidth, nativeHeight;
    private double effectiveScale = 1;
    private long completedWorldFrames;

    public void beginFrame(boolean rendersWorld, RenderTarget destination) {
        RenderSystem.assertOnRenderThread();
        Minecraft mc = Minecraft.getInstance();
        settings = settings();
        settings = NativeRenderScaleProbe.settings(this, settings, rendersWorld);
        boolean active = rendersWorld && mc != null && mc.getWindow() != null && ready(destination)
            && !mc.getWindow().isIconified();
        effectiveScale = policy.frame(settings, System.nanoTime(), active && settings.enabled() && !mc.isPaused()
            && (mc.isWindowActive() || NativeWorldVerification.worldReady()));
        usedThisFrame = false;
        nativeWidth = destination == null ? 0 : destination.width;
        nativeHeight = destination == null ? 0 : destination.height;
        if (!active || !settings.enabled() || Math.abs(effectiveScale - 1) < .0001) release();
    }

    public RenderTarget beginWorld(RenderTarget destination) {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget outline = persistentOutline();
        if (settings == null || mc == null || mc.level == null || mc.getWindow() == null
            || !ready(destination) || !ready(outline)) {
            release();
            return destination;
        }
        nativeWidth = destination.width;
        nativeHeight = destination.height;
        if (!settings.enabled() || Math.abs(effectiveScale - 1) < .0001 || mc.getWindow().isIconified()) return destination;
        int limit = RenderSystem.getDevice().getDeviceInfo().limits().maxTextureSize();
        RenderScalePolicy.Size size = RenderScalePolicy.size(destination.width, destination.height, effectiveScale, limit);
        if (!ready(world) || world.width != size.width() || world.height != size.height()
            || world.getColorTexture().getFormat() != destination.getColorTexture().getFormat()) {
            discardWorldTarget();
            world = new TextureTarget("Lads scaled world", size.width(), size.height(), destination.getColorTexture().getFormat(), destination.getDepthTexture().getFormat());
        }
        // The outline target is the only persistent LevelRenderer attachment. Frame-graph
        // transparency, weather and particle targets derive their size from mainRenderTarget.
        if (scaledOutline != null && scaledOutline != outline) restoreOutline();
        scaledOutline = outline;
        if (outline.width != world.width || outline.height != world.height) outline.resize(world.width, world.height);
        usedThisFrame = true;
        return world;
    }

    public void composite(RenderTarget destination) {
        if (!usedThisFrame || !ready(world) || !ready(destination)) return;
        blit(world, destination, settings.nearest());
        completedWorldFrames++;
    }

    /** Shared by the world pass and the opt-in GPU pixel readback checks. */
    static void blit(RenderTarget source, RenderTarget destination, boolean nearest) {
        try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
            () -> "Lads world resolution composite", destination.getColorTextureView(), Optional.empty())) {
            // Vanilla's unblended fullscreen triangle. No vertex buffer, shader replacement,
            // raw GL state, or Sodium-specific render backend is needed.
            pass.setPipeline(RenderSystem.getCompiledPipeline(RenderPipelines.TRACY_BLIT));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("InSampler", source.getColorTextureView(), RenderSystem.getSamplerCache()
                .getClampToEdge(nearest ? FilterMode.NEAREST : FilterMode.LINEAR));
            pass.draw(3, 1, 0, 0);
        }
    }

    public void endFrame(RenderTarget destination, boolean renderedWorld, boolean succeeded) {
        NativeRenderScaleProbe.endFrame(this, destination, renderedWorld, succeeded);
    }

    public void release() {
        discardWorldTarget();
        restoreOutline();
    }

    private void restoreOutline() {
        RenderTarget outline = scaledOutline;
        scaledOutline = null;
        // Restore only an attachment this controller actually resized. Loading and
        // teardown may have no frame graph or may already have destroyed its textures.
        if (ready(outline) && nativeWidth > 0 && nativeHeight > 0
            && (outline.width != nativeWidth || outline.height != nativeHeight)) outline.resize(nativeWidth, nativeHeight);
    }

    static RenderTarget persistentOutline() {
        var mc = Minecraft.getInstance();
        return mc != null && mc.levelRenderer instanceof RenderScaleTargetsAccessorMixin access
            ? access.lads$persistentOutlineTarget() : null;
    }

    static boolean ready(RenderTarget target) {
        return target != null && target.width > 0 && target.height > 0
            && target.getColorTexture() != null && !target.getColorTexture().isClosed()
            && target.getColorTextureView() != null
            && (!target.hasDepth() || target.getDepthTexture() != null && !target.getDepthTexture().isClosed()
                && target.getDepthTextureView() != null);
    }

    /** Resize/teardown already owns vanilla attachments; never recreate those while closing. */
    public void discardWorldTarget() {
        if (world == null) return;
        world.destroyBuffers();
        world = null;
    }

    public void onVanillaResizeOrClose() {
        discardWorldTarget();
        scaledOutline = null;
    }

    public boolean scaledThisFrame() { return usedThisFrame; }
    public RenderTarget worldTarget() { return world; }
    public double effectiveScale() { return effectiveScale; }
    public long completedWorldFrames() { return completedWorldFrames; }

    private static RenderScalePolicy.Settings settings() {
        int target = switch (NativeQualityOfLife.choice("RenderScale", "Target FPS", 1)) {
            case 0 -> 30; case 1 -> 60; case 2 -> 90; case 3 -> 120; case 4 -> 144; default -> 0;
        };
        return new RenderScalePolicy.Settings(NativeQualityOfLife.enabled("RenderScale"),
            NativeQualityOfLife.choice("RenderScale", "Preset", 0), NativeQualityOfLife.number("RenderScale", "Scale", 100),
            NativeQualityOfLife.choice("RenderScale", "Algorithm", 0) == 1,
            NativeQualityOfLife.bool("RenderScale", "Dynamic Resolution", false), target,
            NativeQualityOfLife.number("RenderScale", "Min Scale", 50));
    }
}
