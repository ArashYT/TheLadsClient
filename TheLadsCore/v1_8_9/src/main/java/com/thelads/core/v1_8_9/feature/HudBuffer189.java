package com.thelads.core.v1_8_9.feature;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * A window-size offscreen buffer for 2D overlay drawing (Autohide's fade, the HUD FPS cap's cache): {@link #begin} draws what
 * follows into it, cleared to transparent black, {@link #end} goes back to the buffer drawn before, and {@link #draw} blends the
 * captured pixels over the screen. Without framebuffers (OptiFine Fast Render or antialiasing) begin is false.
 */
final class HudBuffer189 {
    private final java.nio.IntBuffer viewport = BufferUtils.createIntBuffer(16);
    private final boolean depth;
    private Framebuffer buffer;
    private int previous;

    HudBuffer189(boolean depth) {
        this.depth = depth;
    }

    boolean begin() {
        Minecraft mc = Minecraft.getMinecraft();
        if (!OpenGlHelper.isFramebufferEnabled()) return false;
        // Before creating or resizing the buffer, which binds framebuffer 0 when done.
        previous = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST); // creating the buffer turns depth testing on
        if (buffer == null) {
            buffer = new Framebuffer(mc.displayWidth, mc.displayHeight, depth);
            buffer.setFramebufferColor(0, 0, 0, 0); // transparent black: the composite is premultiplied
        } else if (buffer.framebufferWidth != mc.displayWidth || buffer.framebufferHeight != mc.displayHeight) {
            buffer.createBindFramebuffer(mc.displayWidth, mc.displayHeight);
        }
        if (!depthTest) GlStateManager.disableDepth();
        buffer.framebufferClear();
        buffer.bindFramebuffer(true);
        return true;
    }

    /** Back to the framebuffer and viewport drawn before begin(). */
    void end() {
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, previous);
        GL11.glViewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
    }

    /** The captured pixels blended in at alpha over the scaled GUI area. */
    void draw(float alpha, double width, double height) {
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        GlStateManager.disableDepth();
        GlStateManager.disableAlpha();
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        // Drawn with straight alpha onto transparent black, the captured colours are premultiplied.
        GlStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(alpha, alpha, alpha, alpha);
        buffer.bindFramebufferTexture();
        float u = buffer.framebufferWidth / (float) buffer.framebufferTextureWidth, v = buffer.framebufferHeight / (float) buffer.framebufferTextureHeight;
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
        buffer.unbindFramebufferTexture();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.enableAlpha();
        if (depthTest) GlStateManager.enableDepth();
    }

    boolean allocated() {
        return buffer != null;
    }

    /** Gives the window-size buffer back (it is made again on the next begin). */
    void free() {
        if (buffer == null) return;
        buffer.deleteFramebuffer();
        buffer = null;
    }
}
