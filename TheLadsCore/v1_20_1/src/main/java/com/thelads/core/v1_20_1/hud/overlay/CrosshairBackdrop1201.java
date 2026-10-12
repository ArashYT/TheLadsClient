package com.thelads.core.v1_20_1.hud.overlay;

import com.mojang.blaze3d.platform.NativeImage;
import com.thelads.core.client.CrosshairBackground;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/** Crosshair Tweaks' preview picture (CrosshairBackground): one texture, replaced when another picture is drawn. */
public final class CrosshairBackdrop1201 {
    private static final ResourceLocation ID = new ResourceLocation("theladscore", "crosshair_background");
    private static String source;
    private static int width, height;
    private static boolean ready;

    private CrosshairBackdrop1201() {}

    /** A direct draw: the caller flushes batched fills first. */
    public static boolean draw(GuiGraphics g, String picture, int x, int y, int w, int h) {
        if (picture == null) return false;
        if (!picture.equals(source)) load(picture);
        if (!ready) return false;
        int[] part = CrosshairBackground.cover(width, height, w, h);
        g.setColor(1, 1, 1, 1);
        g.blit(ID, x, y, w, h, part[0], part[1], part[2], part[3], width, height);
        return true;
    }

    private static void load(String picture) {
        source = picture;
        if (ready) Minecraft.getInstance().getTextureManager().release(ID);
        ready = false;
        CrosshairBackground.Picture p = CrosshairBackground.read(picture);
        if (p == null) return;
        NativeImage image = new NativeImage(p.width, p.height, false);
        for (int y = 0; y < p.height; y++) for (int x = 0; x < p.width; x++) {
            int c = p.argb[y * p.width + x];
            image.setPixelRGBA(x, y, 0xFF000000 | (c & 0xFF) << 16 | c & 0xFF00 | (c >> 16) & 0xFF); // ABGR
        }
        DynamicTexture texture = new DynamicTexture(image);
        texture.setFilter(true, false); // drawn scaled
        Minecraft.getInstance().getTextureManager().register(ID, texture);
        width = p.width;
        height = p.height;
        ready = true;
    }
}
