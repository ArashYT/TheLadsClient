package com.thelads.core.v26_2.feature.crosshair;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.thelads.core.client.CrosshairBackground;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Pictures drawn through LadsGraphics.drawImage (CrosshairBackground): Crosshair Tweaks' preview and the Custom Image widget. Two
 * textures, the one drawn longer ago replaced when a third picture is drawn; see-through parts of a picture stay see-through.
 */
public final class CrosshairBackdrop {
    private static final Identifier[] IDS = {Identifier.fromNamespaceAndPath("theladscore", "crosshair_background"),
        Identifier.fromNamespaceAndPath("theladscore", "crosshair_background_2")};
    private static final String[] sources = new String[2];
    private static final int[] widths = new int[2], heights = new int[2];
    private static final boolean[] ready = new boolean[2];
    private static int recent;

    private CrosshairBackdrop() {}

    public static boolean draw(GuiGraphicsExtractor g, String picture, int x, int y, int w, int h) {
        if (picture == null) return false;
        int slot = picture.equals(sources[0]) ? 0 : picture.equals(sources[1]) ? 1 : -1;
        if (slot < 0) load(slot = 1 - recent, picture);
        recent = slot;
        if (!ready[slot]) return false;
        int[] part = CrosshairBackground.cover(widths[slot], heights[slot], w, h);
        g.blit(RenderPipelines.GUI_TEXTURED, IDS[slot], x, y, part[0], part[1], w, h, part[2], part[3], widths[slot], heights[slot]);
        return true;
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
