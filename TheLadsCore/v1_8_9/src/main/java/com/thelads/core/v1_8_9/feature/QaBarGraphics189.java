package com.thelads.core.v1_8_9.feature;

import com.thelads.core.v1_8_9.adapter.GuiLadsAdapter;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

/**
 * QA only: what the QA progress bar draws with on 1.8.9. Its ~20 fills (the backdrop, the bar and its stripes) go out as one draw
 * instead of one Gui.drawRect each (1.9.13: the bar cost ~22 us of a 195 us frame in every benchmark, which players never pay).
 * Fills queue up and are drawn, in order, before anything else this draws and at {@link #flush}; the pixels are Gui.drawRect's.
 */
final class QaBarGraphics189 extends GuiLadsAdapter {
    private float[] quads = new float[32 * 8];
    private int count;

    QaBarGraphics189() {
        super(null, 0, 0);
    }

    @Override
    public void fill(int minX, int minY, int maxX, int maxY, int color) {
        if (count * 8 == quads.length) quads = java.util.Arrays.copyOf(quads, quads.length * 2);
        float[] q = quads;
        int i = count++ * 8;
        q[i] = Math.min(minX, maxX);
        q[i + 1] = Math.min(minY, maxY);
        q[i + 2] = Math.max(minX, maxX);
        q[i + 3] = Math.max(minY, maxY);
        q[i + 4] = (color >> 16 & 255) / 255.0F;
        q[i + 5] = (color >> 8 & 255) / 255.0F;
        q[i + 6] = (color & 255) / 255.0F;
        q[i + 7] = (color >>> 24) / 255.0F;
    }

    @Override
    public void drawText(String text, int x, int y, int color, boolean shadow) {
        flush();
        super.drawText(text, x, y, color, shadow);
    }

    @Override
    public void drawCenteredText(String text, int centerX, int y, int color, boolean shadow) {
        flush();
        super.drawCenteredText(text, centerX, y, color, shadow);
    }

    @Override public void pushPose() { flush(); super.pushPose(); }
    @Override public void popPose() { flush(); super.popPose(); }
    @Override public void translate(float x, float y) { flush(); super.translate(x, y); }
    @Override public void scale(float sx, float sy) { flush(); super.scale(sx, sy); }

    /** Draws the queued fills in one go, in the state GuiLadsAdapter.fill leaves (Gui.drawRect's blend, alpha test back on). */
    void flush() {
        if (count == 0) return;
        GlStateManager.disableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer buffer = tessellator.getWorldRenderer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < count * 8; i += 8) {
            float[] q = quads;
            buffer.pos(q[i], q[i + 3], 0.0D).color(q[i + 4], q[i + 5], q[i + 6], q[i + 7]).endVertex();
            buffer.pos(q[i + 2], q[i + 3], 0.0D).color(q[i + 4], q[i + 5], q[i + 6], q[i + 7]).endVertex();
            buffer.pos(q[i + 2], q[i + 1], 0.0D).color(q[i + 4], q[i + 5], q[i + 6], q[i + 7]).endVertex();
            buffer.pos(q[i], q[i + 1], 0.0D).color(q[i + 4], q[i + 5], q[i + 6], q[i + 7]).endVertex();
        }
        tessellator.draw();
        int last = (count - 1) * 8;
        GlStateManager.color(quads[last + 4], quads[last + 5], quads[last + 6], quads[last + 7]); // the colour Gui.drawRect leaves set
        count = 0;
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
    }

    /** As GuiLadsAdapter.reset, with nothing queued. */
    void begin(FontRenderer font, int width, int height) {
        count = 0;
        reset(font, width, height);
    }
}
