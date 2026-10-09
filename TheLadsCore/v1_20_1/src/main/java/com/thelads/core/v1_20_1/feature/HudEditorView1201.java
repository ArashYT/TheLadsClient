package com.thelads.core.v1_20_1.feature;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

/**
 * The HUD editor's (and Fullbright preview's) game view: this frame's world, without any HUD, scaled into a box. The world is
 * copied into an own render target at the moment the game would draw its HUD (GameRendererViewMixin), and the editor draws that
 * copy as a quad on the next frame. {@code hud}: also vanilla's HUD, scaled into the same box.
 */
public final class HudEditorView1201 {
    private static RenderTarget copy;
    private static boolean wanted, filled;

    private HudEditorView1201() {}

    public static boolean draw(GuiGraphics g, int x, int y, int width, int height) {
        return draw(g, x, y, width, height, true);
    }

    public static boolean draw(GuiGraphics g, int x, int y, int width, int height, boolean hud) {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        if (mc.level == null || main == null || main.width <= 0) return false;
        if (copy == null || copy.width != main.width || copy.height != main.height) {
            release();
            copy = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
            copy.setClearColor(0f, 0f, 0f, 1f);
            copy.setFilterMode(9729); // GL_LINEAR
        }
        wanted = true;
        if (!filled) return false; // the first frame: nothing copied yet, the editor keeps its plain backdrop
        g.flush();
        Matrix4f matrix = g.pose().last().pose();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, copy.getColorTextureId());
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        // Render targets are bottom-up, hence v from 1 at the top to 0 at the bottom.
        buffer.vertex(matrix, x, y + height, 0).uv(0, 0).endVertex();
        buffer.vertex(matrix, x + width, y + height, 0).uv(1, 0).endVertex();
        buffer.vertex(matrix, x + width, y, 0).uv(1, 1).endVertex();
        buffer.vertex(matrix, x, y, 0).uv(0, 1).endVertex();
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.enableBlend();
        if (!hud) return true;
        g.pose().pushPose();
        try {
            g.pose().translate(x, y, 0);
            g.pose().scale(width / (float) g.guiWidth(), height / (float) g.guiHeight(), 1f);
            mc.gui.render(g, mc.getFrameTime()); // the Lads HUD skips itself while the editor is open
        } finally {
            g.pose().popPose();
            g.flush();
        }
        return true;
    }

    /** Render thread, after the world and before the GUI (GameRendererViewMixin). */
    public static void copyWorld(RenderTarget main) {
        if (!wanted) return;
        wanted = false;
        if (copy == null || main == null || copy.width != main.width || copy.height != main.height) return;
        GlStateManager._glBindFramebuffer(36008, main.frameBufferId); // GL_READ_FRAMEBUFFER
        GlStateManager._glBindFramebuffer(36009, copy.frameBufferId); // GL_DRAW_FRAMEBUFFER
        GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height, 0, 0, copy.width, copy.height, 16384, 9728);
        // The sky's alpha is 0 and every GUI texture shader discards that: set alpha to opaque, leaving the colours.
        copy.bindWrite(false);
        GlStateManager._colorMask(false, false, false, true);
        GlStateManager._clearColor(0f, 0f, 0f, 1f);
        GlStateManager._clear(16384, Minecraft.ON_OSX);
        GlStateManager._colorMask(true, true, true, true);
        main.bindWrite(true);
        filled = true;
    }

    public static void release() {
        wanted = false;
        filled = false;
        if (copy == null) return;
        copy.destroyBuffers();
        copy = null;
    }
}
