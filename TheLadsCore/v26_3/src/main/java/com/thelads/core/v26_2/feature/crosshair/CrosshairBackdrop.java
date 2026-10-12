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

/** Crosshair Tweaks' preview picture (CrosshairBackground): one texture, replaced when another picture is drawn. */
public final class CrosshairBackdrop {
    private static final Identifier ID = Identifier.fromNamespaceAndPath("theladscore", "crosshair_background");
    private static String source;
    private static int width, height;
    private static boolean ready;

    private CrosshairBackdrop() {}

    public static boolean draw(GuiGraphicsExtractor g, String picture, int x, int y, int w, int h) {
        if (picture == null) return false;
        if (!picture.equals(source)) load(picture);
        if (!ready) return false;
        int[] part = CrosshairBackground.cover(width, height, w, h);
        g.blit(RenderPipelines.GUI_TEXTURED, ID, x, y, part[0], part[1], w, h, part[2], part[3], width, height);
        return true;
    }

    private static void load(String picture) {
        source = picture;
        if (ready) Minecraft.getInstance().getTextureManager().release(ID);
        ready = false;
        CrosshairBackground.Picture p = CrosshairBackground.read(picture);
        if (p == null) return;
        NativeImage image = new NativeImage(p.width, p.height, false);
        for (int y = 0; y < p.height; y++) for (int x = 0; x < p.width; x++) image.setPixel(x, y, p.argb[y * p.width + x] | 0xFF000000);
        Minecraft.getInstance().getTextureManager().register(ID, new DynamicTexture(() -> "Lads crosshair background", image) {
            { sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR); } // drawn scaled
        });
        width = p.width;
        height = p.height;
        ready = true;
    }
}
