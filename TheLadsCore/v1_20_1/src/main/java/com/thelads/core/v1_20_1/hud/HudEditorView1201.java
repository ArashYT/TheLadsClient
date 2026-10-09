package com.thelads.core.v1_20_1.hud;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;

/**
 * The HUD editor's game view: this frame's world and vanilla HUD, as the main render target holds them when the editor screen draws,
 * scaled into the preview box. A target never samples itself, so the frame is first copied to a second target of the same size.
 */
public final class HudEditorView1201 {
    private static TextureTarget copy;

    private HudEditorView1201() {}

    public static boolean draw(GuiGraphics g, int x, int y, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || width <= 0 || height <= 0) return false;
        RenderTarget main = mc.getMainRenderTarget();
        if (copy == null || copy.width != main.width || copy.height != main.height) {
            release();
            copy = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
            copy.setClearColor(0, 0, 0, 1);
        }
        g.flush();
        Matrix4f projection = RenderSystem.getProjectionMatrix();
        VertexSorting sorting = RenderSystem.getVertexSorting();
        ShaderInstance shader = mc.gameRenderer.blitShader;
        GlStateManager._disableScissorTest();
        try {
            // 1. the main target's colour into the copy (opaque: alpha is not written, the copy is cleared to alpha 1)
            copy.clear(Minecraft.ON_OSX);
            copy.bindWrite(true);
            fullScreen(shader, main, main.width, main.height);
            main.bindWrite(true);
            RenderSystem.setProjectionMatrix(projection, sorting);
        } finally {
            GlStateManager._enableScissorTest();
        }
        // 2. the copy into the box, through the GUI's own pose
        Matrix4f pose = g.pose().last().pose();
        shader.setSampler("DiffuseSampler", copy.getColorTextureId());
        if (shader.MODEL_VIEW_MATRIX != null) shader.MODEL_VIEW_MATRIX.set(RenderSystem.getModelViewMatrix());
        if (shader.PROJECTION_MATRIX != null) shader.PROJECTION_MATRIX.set(RenderSystem.getProjectionMatrix());
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        shader.apply();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buffer.vertex(pose, x, y + height, 0).uv(0, 0).color(255, 255, 255, 255).endVertex();
        buffer.vertex(pose, x + width, y + height, 0).uv(1, 0).color(255, 255, 255, 255).endVertex();
        buffer.vertex(pose, x + width, y, 0).uv(1, 1).color(255, 255, 255, 255).endVertex();
        buffer.vertex(pose, x, y, 0).uv(0, 1).color(255, 255, 255, 255).endVertex();
        BufferUploader.draw(buffer.end());
        shader.clear();
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        return true;
    }

    /** Vanilla's own full-screen copy of a target's colour into the bound target (RenderTarget.blitToScreen, run now). */
    private static void fullScreen(ShaderInstance shader, RenderTarget source, int width, int height) {
        GlStateManager._colorMask(true, true, true, false);
        GlStateManager._disableDepthTest();
        GlStateManager._depthMask(false);
        GlStateManager._viewport(0, 0, width, height);
        GlStateManager._disableBlend();
        shader.setSampler("DiffuseSampler", source.getColorTextureId());
        Matrix4f ortho = new Matrix4f().setOrtho(0.0F, width, height, 0.0F, 1000.0F, 3000.0F);
        RenderSystem.setProjectionMatrix(ortho, VertexSorting.ORTHOGRAPHIC_Z);
        if (shader.MODEL_VIEW_MATRIX != null) shader.MODEL_VIEW_MATRIX.set(new Matrix4f().translation(0.0F, 0.0F, -2000.0F));
        if (shader.PROJECTION_MATRIX != null) shader.PROJECTION_MATRIX.set(ortho);
        shader.apply();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buffer.vertex(0.0, height, 0.0).uv(0, 0).color(255, 255, 255, 255).endVertex();
        buffer.vertex(width, height, 0.0).uv(1, 0).color(255, 255, 255, 255).endVertex();
        buffer.vertex(width, 0.0, 0.0).uv(1, 1).color(255, 255, 255, 255).endVertex();
        buffer.vertex(0.0, 0.0, 0.0).uv(0, 1).color(255, 255, 255, 255).endVertex();
        BufferUploader.draw(buffer.end());
        shader.clear();
        GlStateManager._depthMask(true);
        GlStateManager._colorMask(true, true, true, true);
    }

    public static void release() {
        if (copy == null) return;
        copy.destroyBuffers();
        copy = null;
    }
}
