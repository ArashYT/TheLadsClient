package com.thelads.core.v1_20_1.game;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.thelads.core.client.RenderScalePolicy;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.BetterResolutionModule;
import com.thelads.core.v1_20_1.game.mixin.LevelTargetsAccessor;
import com.thelads.core.v1_20_1.game.mixin.MinecraftTargetAccessor;
import java.io.IOException;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Better Resolution on 1.20.1: the world pass renders into its own target at the chosen scale and is rebuilt at native size
 * (Linear, Nearest, Smooth or Sharp) into the game's main target before the HUD draws, so the HUD and menus stay sharp. The game's
 * main target is swapped for the scaled one only while the world renders; the level renderer's own targets (fabulous graphics,
 * entity outlines) follow its size. Off while a shader pack is in use (the pack owns the whole pipeline), while a post effect
 * (spectator vision) shows, and while another Better Resolution mod is loaded. Works with Sodium and Embeddium: only the target the
 * game hands the level renderer changes.
 */
public final class RenderScale1201 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final RenderScalePolicy POLICY = new RenderScalePolicy();
    private static TextureTarget world;
    private static RenderTarget nativeTarget;
    private static boolean swapped, upscaleFailed, irisChecked, disabled;
    private static ShaderInstance upscale;
    private static Method irisInUse;
    private static Object irisApi;
    private static int worldFilter = -1;
    /** QA: the scale the last world frame used, its size, and how many frames were scaled. */
    public static volatile double lastScale = 1;
    public static volatile int lastWidth, lastHeight;
    public static volatile long scaledFrames;
    /** QA: force a scale (percent, 0 = off) whatever the module says. */
    public static volatile double qaPercent;

    private RenderScale1201() {}

    /** Distant Horizons draws its far terrain into the main target's size; it and a second Better Resolution mod are left alone. */
    private static boolean external() {
        return ModPresence1201.loaded("distanthorizons") || ModPresence1201.loaded("betterresolution") || ModPresence1201.loaded("better_resolution") || ModPresence1201.loaded("better-resolution");
    }

    private static boolean shadersInUse() {
        if (!irisChecked) {
            irisChecked = true;
            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi", true, RenderScale1201.class.getClassLoader());
                irisApi = api.getMethod("getInstance").invoke(null);
                irisInUse = api.getMethod("isShaderPackInUse");
            } catch (Throwable absent) {
                irisInUse = null;
            }
        }
        try {
            return irisInUse != null && (boolean) irisInUse.invoke(irisApi);
        } catch (Throwable broken) {
            return false;
        }
    }

    /** GameRenderer.render, just before the world pass. */
    public static void beginWorld(Minecraft mc, GameRenderer renderer) {
        swapped = false;
        if (disabled) return;
        RenderTarget main = mc.getMainRenderTarget();
        if (!(ModuleManager.getInstance().getModule(BetterResolutionModule.NAME) instanceof BetterResolutionModule module)) return;
        RenderScalePolicy.Settings settings = module.settings(external());
        if (qaPercent > 0) settings = new RenderScalePolicy.Settings(true, 0, qaPercent, settings.method(), false, 0, 50);
        boolean usable = mc.level != null && main.width > 0 && main.height > 0 && renderer.currentEffect() == null && !shadersInUse();
        double scale = POLICY.frame(settings, System.nanoTime(), usable && settings.enabled() && !mc.isPaused());
        if (!usable || !settings.enabled() || Math.abs(scale - 1) < .0001) {
            lastScale = 1;
            resizeLevel(mc, main.width, main.height);
            release();
            return;
        }
        int limit = RenderSystem.maxSupportedTextureSize();
        RenderScalePolicy.Size size = RenderScalePolicy.size(main.width, main.height, scale, limit);
        if (world == null || world.width != size.width() || world.height != size.height()) {
            release();
            world = new TextureTarget(size.width(), size.height(), true, Minecraft.ON_OSX);
            world.setClearColor(0, 0, 0, 0);
            worldFilter = -1;
        }
        int filter = settings.method() == RenderScalePolicy.NEAREST ? 9728 : 9729;
        if (filter != worldFilter) {
            world.setFilterMode(filter);
            worldFilter = filter;
        }
        nativeTarget = main;
        resizeLevel(mc, size.width(), size.height());
        ((MinecraftTargetAccessor) mc).ladsSetMainTarget(world);
        world.clear(Minecraft.ON_OSX); // leaves the target unbound
        world.bindWrite(true);
        swapped = true;
        lastScale = scale;
        lastWidth = size.width();
        lastHeight = size.height();
        method = settings.method();
    }

    private static int method;

    /** GameRenderer.render, after the world pass and its post effects: the scaled world goes onto the game's main target. */
    public static void finishWorld(Minecraft mc) {
        if (!swapped) return;
        swapped = false;
        ((MinecraftTargetAccessor) mc).ladsSetMainTarget(nativeTarget);
        try {
            composite(nativeTarget);
            scaledFrames++;
        } catch (RuntimeException failure) {
            LOGGER.error("Better Resolution: the composite failed; the world is left unscaled until resources reload", failure);
            qaPercent = 0;
            disabled = true;
            release();
            Minecraft.getInstance().execute(() -> resizeLevel(mc, nativeTarget.width, nativeTarget.height));
        }
        nativeTarget.bindWrite(true);
    }

    /** The level renderer's targets (transparency chain, entity outlines) follow the size the world renders at. */
    private static void resizeLevel(Minecraft mc, int width, int height) {
        RenderTarget probe = mc.levelRenderer == null ? null : ((LevelTargetsAccessor) mc.levelRenderer).ladsEntityTarget();
        if (probe != null && (probe.width != width || probe.height != height)) mc.levelRenderer.resize(width, height);
    }

    private static void composite(RenderTarget destination) {
        ShaderInstance shader = upscaleOrNull();
        if (shader == null) {
            compositeBasic(destination);
            return;
        }
        destination.bindWrite(true);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        float w = destination.width, h = destination.height;
        Matrix4f projection = new Matrix4f().setOrtho(0.0f, w, h, 0.0f, 1000.0f, 3000.0f);
        RenderSystem.setProjectionMatrix(projection, com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z);
        shader.setSampler("DiffuseSampler", world.getColorTextureId());
        shader.PROJECTION_MATRIX.set(projection);
        shader.MODEL_VIEW_MATRIX.set(new Matrix4f().translation(0.0f, 0.0f, -2000.0f));
        var mode = shader.getUniform("Mode");
        if (mode != null) mode.set(method == RenderScalePolicy.SHARP ? 1.0f : method == RenderScalePolicy.SMOOTH ? 0.0f : 2.0f);
        shader.apply();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.vertex(0.0, h, 0.0).uv(0.0f, 0.0f).endVertex();
        buffer.vertex(w, h, 0.0).uv(1.0f, 0.0f).endVertex();
        buffer.vertex(w, 0.0, 0.0).uv(1.0f, 1.0f).endVertex();
        buffer.vertex(0.0, 0.0, 0.0).uv(0.0f, 1.0f).endVertex();
        BufferUploader.draw(buffer.end());
        shader.clear();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        GlStateManager._glBindFramebuffer(36160, destination.frameBufferId);
    }

    /** Smooth and Sharp need the upscale shader; without it the vanilla position-texture shader does Linear and Nearest. */
    private static void compositeBasic(RenderTarget destination) {
        destination.bindWrite(true);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        float w = destination.width, h = destination.height;
        RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0.0f, w, h, 0.0f, 1000.0f, 3000.0f), com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z);
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        modelView.translate(0.0f, 0.0f, -2000.0f);
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, world.getColorTextureId());
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.vertex(0.0, h, 0.0).uv(0.0f, 0.0f).endVertex();
        buffer.vertex(w, h, 0.0).uv(1.0f, 0.0f).endVertex();
        buffer.vertex(w, 0.0, 0.0).uv(1.0f, 1.0f).endVertex();
        buffer.vertex(0.0, 0.0, 0.0).uv(0.0f, 1.0f).endVertex();
        BufferUploader.drawWithShader(buffer.end());
        modelView.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    private static ShaderInstance upscaleOrNull() {
        try {
            return upscaleFailed ? null : upscale();
        } catch (IllegalStateException failed) {
            return null; // logged once by upscale()
        }
    }

    /** The upscale shader, made on first use (it is not a vanilla core shader, so the game's own list does not hold it). */
    private static ShaderInstance upscale() {
        if (upscale == null) {
            try {
                upscale = new ShaderInstance(Minecraft.getInstance().getResourceManager(), "lads_world_upscale", DefaultVertexFormat.POSITION_TEX);
            } catch (IOException | RuntimeException failure) {
                if (!upscaleFailed) LOGGER.error("Better Resolution: the upscale shader did not compile", failure);
                upscaleFailed = true;
                throw new IllegalStateException("upscale shader", failure);
            }
        }
        return upscale;
    }

    /** Resources reloaded (or the module switched off): the target and shader are made again when needed. */
    public static void release() {
        if (world != null) {
            world.destroyBuffers();
            world = null;
        }
    }

    public static void reloadShader() {
        if (upscale != null) {
            upscale.close();
            upscale = null;
        }
        upscaleFailed = false;
        disabled = false;
    }

    /** LevelRenderer.doEntityOutline: the outline image is as big as the main target the world renders into. */
    public static int outlineSize(int vanilla, boolean width) {
        if (!swapped) return vanilla;
        return width ? world.width : world.height;
    }
}
