package com.thelads.core.v1_8_9.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * The game view of a Lads screen on 1.8.9 (the HUD editor's backdrop, Fullbright's preview): this frame's world and vanilla HUD,
 * copied from the framebuffer before the screen draws anything over it, and drawn scaled into a box.
 */
final class GameView189 {
    private int view = -1, viewWidth, viewHeight;

    /** Copies the framebuffer (the world and vanilla HUD so far this frame) into the view texture. */
    boolean capture(Minecraft mc) {
        if (mc.theWorld == null) return false;
        int w = mc.displayWidth, h = mc.displayHeight;
        if (view < 0) view = TextureUtil.glGenTextures();
        GlStateManager.bindTexture(view);
        if (w != viewWidth || h != viewHeight) {
            viewWidth = w;
            viewHeight = h;
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB8, w, h, 0, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
        }
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, w, h);
        return true;
    }

    /** The view texture, opaque, in a GUI box; framebuffer rows are bottom-up. */
    boolean draw(int x, int y, int w, int h) {
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.bindTexture(view);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer quad = tessellator.getWorldRenderer();
        quad.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        quad.pos(x, y + h, 0).tex(0, 0).endVertex();
        quad.pos(x + w, y + h, 0).tex(1, 0).endVertex();
        quad.pos(x + w, y, 0).tex(1, 1).endVertex();
        quad.pos(x, y, 0).tex(0, 1).endVertex();
        tessellator.draw();
        return true;
    }

    void release() {
        if (view >= 0) TextureUtil.deleteTexture(view);
        view = -1;
        viewWidth = viewHeight = 0;
    }
}
