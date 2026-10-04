package com.thelads.core.v26_2.feature;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import java.util.Optional;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import com.thelads.core.client.RenderScalePolicy;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.BetterResolutionModule;
import com.thelads.core.v26_2.mixin.RenderScaleTargetsAccessorMixin;

/** A render-thread-owned world attachment. The vanilla presentation/GUI target is never resized here. */
public final class NativeRenderScale {
    /** Better Resolution stands down while its upstream jar is loaded (any id it may use). */
    private static final boolean EXTERNAL = Stream.of("betterresolution", "better_resolution", "better-resolution")
        .anyMatch(FabricLoader.getInstance()::isModLoaded);
    private static final RenderPipeline SMOOTH = upscale(false), SHARP = upscale(true);
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
        settings = ResolutionCapture.settings(this, settings);
        boolean active = rendersWorld && mc != null && mc.getWindow() != null && ready(destination)
            && !mc.getWindow().isMinimized();
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
        if (!settings.enabled() || Math.abs(effectiveScale - 1) < .0001 || mc.getWindow().isMinimized()) return destination;
        int limit = RenderSystem.getDevice().getDeviceInfo().limits().maxTextureSize();
        RenderScalePolicy.Size size = RenderScalePolicy.size(destination.width, destination.height, effectiveScale, limit);
        if (!ready(world) || world.width != size.width() || world.height != size.height()
            || world.getColorTexture().getFormat() != destination.getColorTexture().getFormat()) {
            discardWorldTarget();
            world = new TextureTarget("Lads scaled world", size.width(), size.height(), true, destination.getColorTexture().getFormat());
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
        blit(world, destination, settings.method());
        completedWorldFrames++;
    }

    /** Shared by the world pass and the opt-in GPU pixel readback checks. */
    static void blit(RenderTarget source, RenderTarget destination, int method) {
        try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
            () -> "Lads world resolution composite", destination.getColorTextureView(), Optional.empty())) {
            // Vanilla's unblended fullscreen triangle. No vertex buffer, raw GL state,
            // or Sodium-specific render backend is needed.
            pass.setPipeline(pipeline(method));
            RenderSystem.bindDefaultUniforms(pass);
            pass.bindTexture("InSampler", source.getColorTextureView(), RenderSystem.getSamplerCache()
                .getClampToEdge(method == RenderScalePolicy.NEAREST ? FilterMode.NEAREST : FilterMode.LINEAR));
            pass.draw(3, 1, 0, 0);
        }
    }

    /** Smooth and Sharp: vanilla's fullscreen triangle with Better Resolution's fragment shader (core/world_upscale.fsh). */
    private static RenderPipeline upscale(boolean sharp) {
        var builder = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("theladscore", "pipeline/world_" + (sharp ? "sharp" : "smooth")))
            .withVertexShader("core/screenquad")
            .withFragmentShader(Identifier.fromNamespaceAndPath("theladscore", "core/world_upscale"))
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES);
        if (sharp) builder.withShaderDefine("LADS_SHARP");
        return builder.build();
    }

    /** The method's pass. Linear, Nearest and a shader that does not compile (the device logs it once) use vanilla's blit. */
    static RenderPipeline pipeline(int method) {
        RenderPipeline custom = method == RenderScalePolicy.SMOOTH ? SMOOTH : method == RenderScalePolicy.SHARP ? SHARP : null;
        return custom != null && RenderSystem.getDevice().precompilePipeline(custom).isValid() ? custom : RenderPipelines.TRACY_BLIT;
    }

    /** QA: whether Smooth and Sharp compiled (blit falls back to Linear otherwise). */
    static boolean upscaleReady() { return pipeline(RenderScalePolicy.SMOOTH) == SMOOTH && pipeline(RenderScalePolicy.SHARP) == SHARP; }

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
            && (!target.useDepth || target.getDepthTexture() != null && !target.getDepthTexture().isClosed()
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
        return ((BetterResolutionModule) ModuleManager.getInstance().getModule(BetterResolutionModule.NAME)).settings(EXTERNAL);
    }
}
