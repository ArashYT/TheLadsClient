package com.thelads.core.v1_8_9.gui;

import com.thelads.core.client.CrosshairBackground;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** Crosshair Tweaks' preview picture (CrosshairBackground): one texture, replaced when another picture is drawn. */
public final class CrosshairBackdrop189 {
    private static String source;
    private static ResourceLocation texture;
    private static int width, height;

    private CrosshairBackdrop189() {}

    public static boolean draw(String picture, int x, int y, int w, int h) {
        if (!picture.equals(source)) load(picture);
        if (texture == null) return false;
        int[] part = CrosshairBackground.cover(width, height, w, h);
        Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR); // drawn scaled
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GlStateManager.enableTexture2D();
        GlStateManager.color(1, 1, 1, 1);
        Gui.drawScaledCustomSizeModalRect(x, y, part[0], part[1], part[2], part[3], w, h, width, height);
        return true;
    }

    private static void load(String picture) {
        source = picture;
        if (texture != null) Minecraft.getMinecraft().getTextureManager().deleteTexture(texture);
        texture = null;
        CrosshairBackground.Picture p = CrosshairBackground.read(picture);
        if (p == null) return;
        DynamicTexture dynamic = new DynamicTexture(p.width, p.height);
        int[] data = dynamic.getTextureData();
        for (int i = 0; i < data.length; i++) data[i] = p.argb[i] | 0xFF000000;
        dynamic.updateDynamicTexture();
        texture = Minecraft.getMinecraft().getTextureManager().getDynamicTextureLocation("lads_crosshair_background", dynamic);
        width = p.width;
        height = p.height;
    }
}
