package com.thelads.core.v1_8_9.feature;

import com.thelads.core.client.RenderScalePolicy;
import com.thelads.core.modules.BetterResolutionModule;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLContext;

/**
 * Better Resolution (1.6.0's RenderScale) on 1.8.9, as 26.x NativeRenderScale with the shared RenderScalePolicy: the world
 * (sky, terrain, entities, particles, weather and what mods draw in RenderWorldLastEvent) renders into a framebuffer of its own at the module's scale,
 * stretched over Minecraft's framebuffer before the hand, HUD and screens are drawn at native resolution; Minecraft's
 * framebuffer, window and GUI scale never change. Forge events only, as OptiFine rewrites EntityRenderer: the world pass
 * starts in FogColors (after its viewport, before its clear) and ends in RenderHandEvent (after RenderWorldLastEvent), so
 * on 1.8.9 the hand stays at native resolution. Off while 1.8.9 renders without framebuffers (OptiFine's Fast Render or
 * antialiasing), with an OptiFine shader pack, in 3D anaglyph and while a spectator's entity outlines are shown: each of
 * those binds its own targets in the world pass. Smooth and Sharp composite through a GLSL 1.20 program built from the shared
 * shaders/include/world_upscale.glsl; without OpenGL 2.0 (or if it fails to compile) they composite as Linear.
 */
public final class RenderScale189 {
    private final RenderScalePolicy policy = new RenderScalePolicy();
    private Framebuffer world;
    private boolean active, started, bound;
    private int method;
    private static int textureLimit;
    /** Smooth's and Sharp's programs once built; 0 where they cannot run. */
    private static int[] programs;
    private static Method optifineShaders;
    private static boolean optifineChecked;
    /** QA only: composited world frames, and the world framebuffer's size in the last one. */
    static long scaledFrames;
    static int scaledWidth, scaledHeight;

    @SubscribeEvent
    public void frame(TickEvent.RenderTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.phase == TickEvent.Phase.END) {
            if (bound) composite(mc); // no RenderHandEvent this frame: never leave the screen in the world framebuffer
            return;
        }
        started = false;
        RenderScalePolicy.Settings settings = settings();
        boolean rendersWorld = mc.theWorld != null && !mc.skipRenderWorld && Display.isVisible() && OpenGlHelper.isFramebufferEnabled()
            && !mc.gameSettings.anaglyph && !shaders() && !(mc.thePlayer != null && mc.thePlayer.isSpectator()
            && mc.gameSettings.keyBindSpectatorOutlines.isKeyDown());
        double scale = policy.frame(settings, System.nanoTime(), rendersWorld && settings.enabled() && !mc.isGamePaused() && Display.isActive());
        method = settings.method();
        active = rendersWorld && settings.enabled() && Math.abs(scale - 1) >= .0001;
        if (!active) {
            release(mc);
            return;
        }
        if (textureLimit == 0) textureLimit = GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE);
        RenderScalePolicy.Size size = RenderScalePolicy.size(mc.displayWidth, mc.displayHeight, scale, textureLimit);
        scaledWidth = size.width();
        scaledHeight = size.height();
    }

    /** EntityRenderer.updateFogColor, between the world pass's viewport and clear: the pass goes to the world framebuffer. */
    @SubscribeEvent
    public void worldStart(EntityViewRenderEvent.FogColors event) {
        if (!active || started) return;
        started = true;
        if (world == null) world = new Framebuffer(scaledWidth, scaledHeight, true);
        else if (world.framebufferWidth != scaledWidth || world.framebufferHeight != scaledHeight) world.createBindFramebuffer(scaledWidth, scaledHeight);
        int filter = method == RenderScalePolicy.NEAREST ? GL11.GL_NEAREST : GL11.GL_LINEAR;
        if (world.framebufferFilter != filter) world.setFramebufferFilter(filter);
        world.bindFramebuffer(true);
        bound = true;
    }

    /** First of all handlers, even cancelled: the world is done (RenderWorldLastEvent included) and the hand comes next. */
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void worldEnd(RenderHandEvent event) {
        if (bound) composite(Minecraft.getMinecraft());
    }

    /** Minecraft's framebuffer again, the world stretched over it; matrices and the states the world pass leaves as found. */
    private void composite(Minecraft mc) {
        bound = false;
        mc.getFramebuffer().bindFramebuffer(true);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND), alpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST), depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        mc.entityRenderer.disableLightmap();
        GlStateManager.disableFog();
        GlStateManager.disableBlend();
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.pushMatrix();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        int program = program(method);
        if (program != 0) {
            GL20.glUseProgram(program);
            GL20.glUniform1i(GL20.glGetUniformLocation(program, "world"), 0);
            GL20.glUniform2f(GL20.glGetUniformLocation(program, "texel"), 1f / world.framebufferTextureWidth, 1f / world.framebufferTextureHeight);
        }
        world.framebufferRenderExt(mc.displayWidth, mc.displayHeight, false);
        if (program != 0) GL20.glUseProgram(0);
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.popMatrix();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.popMatrix();
        if (blend) GlStateManager.enableBlend();
        if (alpha) GlStateManager.enableAlpha();
        if (depth) GlStateManager.enableDepth();
        scaledFrames++;
    }

    /** Between frames, with Minecraft's framebuffer bound: deleting one unbinds every framebuffer. */
    private void release(Minecraft mc) {
        if (world == null) return;
        world.deleteFramebuffer();
        world = null;
        mc.getFramebuffer().bindFramebuffer(true);
    }

    /** Better Resolution has no 1.8.9 release of its own, so there is no upstream jar to stand down for. */
    private static RenderScalePolicy.Settings settings() {
        return ((BetterResolutionModule) Options189.module(BetterResolutionModule.NAME)).settings(false);
    }

    /** The method's program, built on first use; 0 for Linear and Nearest (the framebuffer's own filter) and where it cannot run. */
    static int program(int method) {
        if (method != RenderScalePolicy.SMOOTH && method != RenderScalePolicy.SHARP) return 0;
        if (programs == null) programs = GLContext.getCapabilities().OpenGL20 ? new int[]{compile(false), compile(true)} : new int[2];
        return programs[method == RenderScalePolicy.SHARP ? 1 : 0];
    }

    private static int compile(boolean sharp) {
        try (InputStream include = RenderScale189.class.getResourceAsStream("/assets/theladscore/shaders/include/world_upscale.glsl")) {
            int vertex = shader(GL20.GL_VERTEX_SHADER, "#version 120\nvarying vec2 uv;\n"
                + "void main() { gl_Position = ftransform(); uv = gl_MultiTexCoord0.st; }\n");
            int fragment = shader(GL20.GL_FRAGMENT_SHADER, "#version 120\n#define texture texture2D\n" + IOUtils.toString(include, StandardCharsets.UTF_8)
                + "\nuniform sampler2D world;\nuniform vec2 texel;\nvarying vec2 uv;\n"
                + "void main() { gl_FragColor = " + (sharp ? "ladsSharp" : "ladsSmooth") + "(world, uv, texel); }\n");
            int program = GL20.glCreateProgram();
            GL20.glAttachShader(program, vertex);
            GL20.glAttachShader(program, fragment);
            GL20.glLinkProgram(program);
            GL20.glDeleteShader(vertex);
            GL20.glDeleteShader(fragment);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) != GL11.GL_TRUE) throw new IllegalStateException(GL20.glGetProgramInfoLog(program, 4096));
            return program;
        } catch (Exception failed) {
            LogManager.getLogger("TheLadsCore").error("Better Resolution: the {} shader did not compile; using Linear", sharp ? "Sharp" : "Smooth", failed);
            return 0;
        }
    }

    private static int shader(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) != GL11.GL_TRUE) throw new IllegalStateException(GL20.glGetShaderInfoLog(shader, 4096));
        return shader;
    }

    /** OptiFine's Config.isShaders(): a shader pack renders the world through its own framebuffers. */
    private static boolean shaders() {
        if (!optifineChecked) {
            optifineChecked = true;
            try { optifineShaders = Class.forName("Config").getMethod("isShaders"); } catch (Throwable notOptiFine) { optifineShaders = null; }
        }
        try { return optifineShaders != null && (Boolean) optifineShaders.invoke(null); } catch (Throwable failed) { return false; }
    }
}
