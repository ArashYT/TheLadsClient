package com.thelads.core.v1_8_9.feature;

import java.nio.ByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

/**
 * A window-size offscreen buffer for 2D overlay drawing (Autohide's fade, the HUD FPS cap's cache): {@link #begin} draws what
 * follows into it, cleared to transparent black, {@link #end} goes back to the buffer drawn before, and {@link #draw} blends the
 * captured pixels over the screen. Its own framebuffer object, made with OpenGlHelper's raw calls: vanilla's Framebuffer does
 * nothing while OptiFine Fast Render (Lads' default) or antialiasing is on, which used to switch the HUD cache off for most
 * players (1.9.12). Without framebuffer support in the driver begin is false.
 */
final class HudBuffer189 {
    private static final int FRAMEBUFFER_BINDING = 0x8CA6; // GL_FRAMEBUFFER_BINDING (= _EXT)
    private final boolean depth;
    private int fbo = -1, texture = -1, depthBuffer = -1, width, height;
    private int previous;
    private int vpX, vpY, vpW, vpH;
    /** previous and viewport are read once until newFrame(): every capture in a frame begins on the same framebuffer and viewport. */
    private boolean queried;

    HudBuffer189(boolean depth) {
        this.depth = depth;
    }

    boolean begin() {
        Minecraft mc = Minecraft.getMinecraft();
        if (!OpenGlHelper.framebufferSupported) return false;
        if (!queried) {
            queried = true;
            previous = GL11.glGetInteger(FRAMEBUFFER_BINDING); // Minecraft's framebuffer, or 0 (the window) under Fast Render
            vpX = 0;
            vpY = 0;
            vpW = mc.displayWidth;
            vpH = mc.displayHeight;
        }
        if ((fbo < 0 || width != mc.displayWidth || height != mc.displayHeight) && !create(mc.displayWidth, mc.displayHeight)) {
            end();
            return false;
        }
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, fbo);
        GL11.glViewport(0, 0, width, height);
        GlStateManager.clearColor(0, 0, 0, 0); // transparent black: the composite is premultiplied
        int mask = GL11.GL_COLOR_BUFFER_BIT;
        if (depth) {
            GlStateManager.clearDepth(1.0D);
            mask |= GL11.GL_DEPTH_BUFFER_BIT;
        }
        GlStateManager.clear(mask);
        return true;
    }

    /** What vanilla's Framebuffer.createFramebuffer makes (RGBA8 texture, nearest, clamped; depth renderbuffer), ungated. */
    private boolean create(int w, int h) {
        free();
        width = w;
        height = h;
        fbo = OpenGlHelper.glGenFramebuffers();
        texture = TextureUtil.glGenTextures();
        GlStateManager.bindTexture(texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, 0x8058 /* GL_RGBA8 */, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GlStateManager.bindTexture(0);
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, fbo);
        OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER, OpenGlHelper.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
        if (depth) {
            depthBuffer = OpenGlHelper.glGenRenderbuffers();
            OpenGlHelper.glBindRenderbuffer(OpenGlHelper.GL_RENDERBUFFER, depthBuffer);
            OpenGlHelper.glRenderbufferStorage(OpenGlHelper.GL_RENDERBUFFER, 0x81A6 /* GL_DEPTH_COMPONENT24 */, w, h);
            OpenGlHelper.glFramebufferRenderbuffer(OpenGlHelper.GL_FRAMEBUFFER, OpenGlHelper.GL_DEPTH_ATTACHMENT, OpenGlHelper.GL_RENDERBUFFER, depthBuffer);
        }
        if (OpenGlHelper.glCheckFramebufferStatus(OpenGlHelper.GL_FRAMEBUFFER) == OpenGlHelper.GL_FRAMEBUFFER_COMPLETE) return true;
        free();
        return false;
    }

    /** The next begin() reads the framebuffer and viewport to return to again. */
    void newFrame() {
        queried = false;
    }

    /** Back to the framebuffer and viewport drawn before begin(). */
    void end() {
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, previous);
        GL11.glViewport(vpX, vpY, vpW, vpH);
    }

    /** The captured pixels blended in at alpha over the scaled GUI area. */
    void draw(float alpha, double width, double height) {
        boolean depthTest = GlState189.depth();
        GlStateManager.disableDepth();
        GlStateManager.disableAlpha();
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        // Drawn with straight alpha onto transparent black, the captured colours are premultiplied.
        GlStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(alpha, alpha, alpha, alpha);
        GlStateManager.bindTexture(texture);
        float u = 1, v = 1;
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer quad = tessellator.getWorldRenderer();
        // The overlay's own matrix (EntityRenderer.setupOverlayRendering): an element can end with its matrix still pushed (Raised189
        // pops in the same Post), and the captured pixels already sit where that matrix drew them.
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.translate(0.0F, 0.0F, -2000.0F);
        quad.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        quad.pos(0, height, 0).tex(0, 0).endVertex();
        quad.pos(width, height, 0).tex(u, 0).endVertex();
        quad.pos(width, 0, 0).tex(u, v).endVertex();
        quad.pos(0, 0, 0).tex(0, v).endVertex();
        tessellator.draw();
        GlStateManager.popMatrix();
        GlStateManager.bindTexture(0);
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.enableAlpha();
        if (depthTest) GlStateManager.enableDepth();
    }

    boolean allocated() {
        return fbo >= 0;
    }

    /** Gives the window-size buffer back (it is made again on the next begin). */
    void free() {
        if (depthBuffer >= 0) OpenGlHelper.glDeleteRenderbuffers(depthBuffer);
        if (texture >= 0) TextureUtil.deleteTexture(texture);
        if (fbo >= 0) OpenGlHelper.glDeleteFramebuffers(fbo);
        fbo = texture = depthBuffer = -1;
    }
}
