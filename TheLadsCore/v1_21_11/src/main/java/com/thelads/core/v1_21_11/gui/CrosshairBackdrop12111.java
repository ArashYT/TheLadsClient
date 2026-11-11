package com.thelads.core.v1_21_11.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.thelads.core.client.CrosshairBackground;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Pictures drawn through LadsGraphics.drawImage (CrosshairBackground): Crosshair Tweaks' preview, the Custom Image widget and DM
 * pictures (drawImageCell: a GIF's frames). Eight textures, the one drawn longest ago replaced by a new picture; see-through
 * parts of a picture stay see-through.
 */
public final class CrosshairBackdrop12111 {
    private static final int SLOTS = 8;
    private static final Identifier[] IDS = new Identifier[SLOTS];
    static { for (int i = 0; i < SLOTS; i++) IDS[i] = Identifier.fromNamespaceAndPath("theladscore", i == 0 ? "crosshair_background" : "crosshair_background_" + (i + 1)); }
    private static final String[] sources = new String[SLOTS];
    private static final int[] widths = new int[SLOTS], heights = new int[SLOTS];
    private static final boolean[] ready = new boolean[SLOTS];
    private static final long[] used = new long[SLOTS];
    private static long clock;

    private CrosshairBackdrop12111() {}

    public static boolean draw(GuiGraphics g, String picture, int x, int y, int w, int h) {
        return drawCell(g, picture, 1, 1, 0, x, y, w, h);
    }

    /** Cell number cell of the picture split into cols x rows equal cells (row by row), covering the box. */
    public static boolean drawCell(GuiGraphics g, String picture, int cols, int rows, int cell, int x, int y, int w, int h) {
        if (picture == null || cols < 1 || rows < 1) return false;
        int slot = slot(picture);
        int cw = widths[slot] / cols, ch = heights[slot] / rows;
        if (!ready[slot] || cw < 1 || ch < 1) return false;
        int u = cell % cols * cw, v = cell / cols % rows * ch;
        int[] part = CrosshairBackground.cover(cw, ch, w, h);
        g.blit(RenderPipelines.GUI_TEXTURED, IDS[slot], x, y, u + part[0], v + part[1], w, h, part[2], part[3], widths[slot], heights[slot]);
        return true;
    }

    private static int slot(String picture) {
        int oldest = 0;
        for (int i = 0; i < SLOTS; i++) {
            if (picture.equals(sources[i])) {
                used[i] = ++clock;
                return i;
            }
            if (used[i] < used[oldest]) oldest = i;
        }
        load(oldest, picture);
        used[oldest] = ++clock;
        return oldest;
    }

    private static void load(int slot, String picture) {
        sources[slot] = picture;
        if (ready[slot]) Minecraft.getInstance().getTextureManager().release(IDS[slot]);
        ready[slot] = false;
        CrosshairBackground.Picture p = CrosshairBackground.read(picture);
        if (p == null) return;
        NativeImage image = new NativeImage(p.width, p.height, false);
        for (int y = 0; y < p.height; y++) for (int x = 0; x < p.width; x++) image.setPixel(x, y, p.argb[y * p.width + x]);
        Minecraft.getInstance().getTextureManager().register(IDS[slot], new DynamicTexture(() -> "Lads picture", image) {
            { sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR); } // drawn scaled
        });
        widths[slot] = p.width;
        heights[slot] = p.height;
        ready[slot] = true;
    }
}
