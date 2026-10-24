package com.thelads.core.v26_2.pvp;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.thelads.core.client.ColorGrade;
import com.thelads.core.client.ScreenEffects;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.ColorSaturationModule;
import com.thelads.core.modules.MotionBlurModule;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;
import org.slf4j.LoggerFactory;

/**
 * Color Saturation and Motion Blur on 26.x: fullscreen passes over the main target once the world (hand included) is drawn and before
 * the HUD (RenderScaleMixin's finishWorld, after Better Resolution's composite). Each pass reads a copy of the picture
 * ({@link #scene}); Motion Blur also reads the last finished frame ({@link #history}) and copies this one there after. The sliders reach
 * the shaders (theladscore:core/screen_color, screen_motion) through small uniform buffers written only when they change (the motion
 * blend every frame). Works on Vulkan and OpenGL; stands aside while an Iris shader pack renders the world. Menu Blur is vanilla's
 * own blur on 26.x (PvpVisuals26).
 */
public final class ScreenEffects26 {
    private static final boolean IRIS = FabricLoader.getInstance().isModLoaded("iris");
    private static final BindGroupLayout COLOR_UNIFORMS = BindGroupLayout.builder().withUniform("LadsColor", UniformType.UNIFORM_BUFFER).build();
    private static final BindGroupLayout MOTION_UNIFORMS = BindGroupLayout.builder().withUniform("LadsMotion", UniformType.UNIFORM_BUFFER)
        .withUniform("PrevSampler", UniformType.COMBINED_IMAGE_SAMPLER).build();
    private static final RenderPipeline COLOR = pipeline("screen_color", COLOR_UNIFORMS), MOTION = pipeline("screen_motion", MOTION_UNIFORMS);
    /** std140: three vec4 rows and a float (vibrance) for color; one float (how much of the last frame stays) for motion. */
    private static final int COLOR_BYTES = 64, MOTION_BYTES = 16;
    private static final ColorGrade GRADE = new ColorGrade();
    private static final ByteBuffer COLOR_DATA = ByteBuffer.allocateDirect(COLOR_BYTES).order(ByteOrder.nativeOrder());
    private static final ByteBuffer MOTION_DATA = ByteBuffer.allocateDirect(MOTION_BYTES).order(ByteOrder.nativeOrder());
    private static RenderTarget scene, history;
    private static GpuBuffer colorBuffer, motionBuffer;
    private static GpuBufferSlice colorSlice, motionSlice;
    private static boolean colorStale = true, historyFresh = true, failed;
    private static long frameStart;
    private static ColorSaturationModule colorModule;
    private static MotionBlurModule motionModule;

    private ScreenEffects26() {}

    private static RenderPipeline pipeline(String name, BindGroupLayout uniforms) {
        return RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("theladscore", "pipeline/" + name))
            .withVertexShader("core/screenquad")
            .withFragmentShader(Identifier.fromNamespaceAndPath("theladscore", "core/" + name))
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
            .withBindGroupLayout(uniforms)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .build();
    }

    /** GameRenderer.render: the world is done on {@code main}, the HUD comes next. */
    public static void afterWorld(RenderTarget main) {
        long now = System.nanoTime(), frame = now - frameStart;
        frameStart = now;
        if (colorModule == null) {
            colorModule = (ColorSaturationModule) ModuleManager.getInstance().getModule(ColorSaturationModule.NAME);
            motionModule = (MotionBlurModule) ModuleManager.getInstance().getModule(MotionBlurModule.NAME);
        }
        boolean color = colorModule.isEnabled(), motion = motionModule.isEnabled();
        if (color && GRADE.update(colorModule)) colorStale = true;
        color &= GRADE.active;
        if (!motion) historyFresh = true; // turned back on, it starts from the frame it sees first
        if (!color && !motion) {
            if (scene != null) release(); // two screen-sized textures and two buffers back while both are off
            return;
        }
        if (failed || Minecraft.getInstance().level == null || main == null || main.getColorTexture() == null
            || IRIS && net.irisshaders.iris.api.v0.IrisApi.getInstance().isShaderPackInUse()) return;
        try {
            if (scene == null || scene.width != main.width || scene.height != main.height) resize(main);
            var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
            if (color) {
                if (colorStale) uploadColor();
                scene.copyColorFrom(main);
                CompiledRenderPipeline compiled = RenderSystem.getCompiledPipelineNullable(COLOR);
                if (compiled == null) throw new IllegalStateException("theladscore:core/screen_color did not compile");
                try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Lads color saturation", main.getColorTextureView(), Optional.empty())) {
                    pass.setPipeline(compiled);
                    RenderSystem.bindDefaultUniforms(pass);
                    pass.setUniform("InSampler", scene.getColorTextureView(), sampler);
                    pass.setUniform("LadsColor", colorBuffer);
                    pass.draw(3, 1, 0, 0);
                }
            }
            if (motion) {
                if (historyFresh) {
                    history.copyColorFrom(main);
                    historyFresh = false;
                    return;
                }
                MOTION_DATA.putFloat(0, ScreenEffects.motionRetain(motionModule.strength.getValue(), frame));
                RenderSystem.getDevice().createCommandEncoder().writeToBuffer(motionSlice, MOTION_DATA);
                scene.copyColorFrom(main);
                CompiledRenderPipeline compiled = RenderSystem.getCompiledPipelineNullable(MOTION);
                if (compiled == null) throw new IllegalStateException("theladscore:core/screen_motion did not compile");
                try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Lads motion blur", main.getColorTextureView(), Optional.empty())) {
                    pass.setPipeline(compiled);
                    RenderSystem.bindDefaultUniforms(pass);
                    pass.setUniform("InSampler", scene.getColorTextureView(), sampler);
                    pass.setUniform("PrevSampler", history.getColorTextureView(), sampler);
                    pass.setUniform("LadsMotion", motionBuffer);
                    pass.draw(3, 1, 0, 0);
                }
                history.copyColorFrom(main);
            }
        } catch (RuntimeException broken) {
            LoggerFactory.getLogger("TheLadsCore").error("Screen effects failed; Color Saturation and Motion Blur stay off until the next restart", broken);
            failed = true;
            release();
        }
    }

    /** The window changed size: the copies follow it (and the motion trail starts again). */
    private static void resize(RenderTarget main) {
        release();
        var format = main.getColorTexture().getFormat();
        scene = new TextureTarget("Lads screen effects", main.width, main.height, format, null);
        history = new TextureTarget("Lads motion blur", main.width, main.height, format, null);
        var device = RenderSystem.getDevice();
        colorBuffer = device.createBuffer(() -> "Lads color saturation", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, COLOR_BYTES);
        motionBuffer = device.createBuffer(() -> "Lads motion blur", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, MOTION_BYTES);
        colorSlice = colorBuffer.slice();
        motionSlice = motionBuffer.slice();
        colorStale = true;
        historyFresh = true;
    }

    private static void uploadColor() {
        float[] m = GRADE.matrix;
        for (int i = 0; i < 12; i++) COLOR_DATA.putFloat(i * 4, m[i]);
        COLOR_DATA.putFloat(48, GRADE.vibrance);
        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(colorSlice, COLOR_DATA);
        colorStale = false;
    }

    private static void release() {
        if (scene != null) scene.destroyBuffers();
        if (history != null) history.destroyBuffers();
        if (colorBuffer != null) colorBuffer.close();
        if (motionBuffer != null) motionBuffer.close();
        scene = history = null;
        colorBuffer = motionBuffer = null;
    }
}
