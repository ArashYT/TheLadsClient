package com.thelads.core.v1_20_1.hud.overlay;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.thelads.core.client.CrosshairBackground;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Pictures drawn through LadsGraphics.drawImage (CrosshairBackground): Crosshair Tweaks' preview and the Custom Image widget. Two
 * textures, the one drawn longer ago replaced when a third picture is drawn; see-through parts of a picture stay see-through.
 */
public final class CrosshairBackdrop1201 {
    private static final ResourceLocation[] IDS = {new ResourceLocation("theladscore", "crosshair_background"),
        new ResourceLocation("theladscore", "crosshair_background_2")};
    private static final String[] sources = new String[2];
    private static final int[] widths = new int[2], heights = new int[2];
    private static final boolean[] ready = new boolean[2];
    private static int recent;

    private CrosshairBackdrop1201() {}

    /** A direct draw: the caller flushes batched fills first. */
    public static boolean draw(GuiGraphics g, String picture, int x, int y, int w, int h) {
        if (picture == null) return false;
        int slot = picture.equals(sources[0]) ? 0 : picture.equals(sources[1]) ? 1 : -1;
        if (slot < 0) load(slot = 1 - recent, picture);
        recent = slot;
        if (!ready[slot]) return false;
        int[] part = CrosshairBackground.cover(widths[slot], heights[slot], w, h);
        g.setColor(1, 1, 1, 1);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(IDS[slot], x, y, w, h, part[0], part[1], part[2], part[3], widths[slot], heights[slot]);
        return true;
    }

    private static void load(int slot, String picture) {
        sources[slot] = picture;
        if (ready[slot]) Minecraft.getInstance().getTextureManager().release(IDS[slot]);
        ready[slot] = false;
        CrosshairBackground.Picture p = CrosshairBackground.read(picture);
        if (p == null) return;
        NativeImage image = new NativeImage(p.width, p.height, false);
        for (int y = 0; y < p.height; y++) for (int x = 0; x < p.width; x++) {
            int c = p.argb[y * p.width + x];
            image.setPixelRGBA(x, y, c & 0xFF000000 | (c & 0xFF) << 16 | c & 0xFF00 | (c >> 16) & 0xFF); // ABGR
        }
        DynamicTexture texture = new DynamicTexture(image);
        texture.setFilter(true, false); // drawn scaled
        Minecraft.getInstance().getTextureManager().register(IDS[slot], texture);
        widths[slot] = p.width;
        heights[slot] = p.height;
        ready[slot] = true;
    }
}
