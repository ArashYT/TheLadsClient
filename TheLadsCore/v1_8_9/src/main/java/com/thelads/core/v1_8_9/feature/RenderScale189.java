package com.thelads.core.v1_8_9.feature;

import com.thelads.core.client.RenderScalePolicy;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

/**
 * RenderScale on 1.8.9, as 26.x NativeRenderScale with the shared RenderScalePolicy: the world (sky, terrain, entities,
 * particles, weather and what mods draw in RenderWorldLastEvent) renders into a framebuffer of its own at the module's scale,
 * stretched over Minecraft's framebuffer before the hand, HUD and screens are drawn at native resolution; Minecraft's
 * framebuffer, window and GUI scale never change. Forge events only, as OptiFine rewrites EntityRenderer: the world pass
 * starts in FogColors (after its viewport, before its clear) and ends in RenderHandEvent (after RenderWorldLastEvent), so
 * on 1.8.9 the hand stays at native resolution. Off while 1.8.9 renders without framebuffers (OptiFine's Fast Render or
 * antialiasing), with an OptiFine shader pack, in 3D anaglyph and while a spectator's entity outlines are shown: each of
 * those binds its own targets in the world pass.
 */
public final class RenderScale189 {
    private static final int[] TARGET_FPS = {30, 60, 90, 120, 144, 0};
    private final RenderScalePolicy policy = new RenderScalePolicy();
    private Framebuffer world;
    private boolean active, started, bound;
    private boolean nearest;
    private static int textureLimit;
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
        nearest = settings.nearest();
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
        int filter = nearest ? GL11.GL_NEAREST : GL11.GL_LINEAR;
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
        world.framebufferRenderExt(mc.displayWidth, mc.displayHeight, false);
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

    private static RenderScalePolicy.Settings settings() {
        int target = TARGET_FPS[Math.max(0, Math.min(TARGET_FPS.length - 1, Options189.choice("RenderScale", "Target FPS", 1)))];
        return new RenderScalePolicy.Settings(Options189.enabled("RenderScale"), Options189.choice("RenderScale", "Preset", 0),
            Options189.number("RenderScale", "Scale", 100), Options189.choice("RenderScale", "Algorithm", 0) == 1,
            Options189.bool("RenderScale", "Dynamic Resolution", false), target, Options189.number("RenderScale", "Min Scale", 50));
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
