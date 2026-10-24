package com.thelads.core.v1_8_9.gui;

import com.thelads.core.client.CrosshairBackground;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * Pictures drawn through LadsGraphics.drawImage (CrosshairBackground): Crosshair Tweaks' preview and the Custom Image widget. Two
 * textures, the one drawn longer ago replaced when a third picture is drawn; see-through parts of a picture stay see-through.
 */
public final class CrosshairBackdrop189 {
    private static final String[] sources = new String[2];
    private static final ResourceLocation[] textures = new ResourceLocation[2];
    private static final int[] widths = new int[2], heights = new int[2];
    private static int recent;

    private CrosshairBackdrop189() {}

    public static boolean draw(String picture, int x, int y, int w, int h) {
        if (picture == null) return false;
        int slot = picture.equals(sources[0]) ? 0 : picture.equals(sources[1]) ? 1 : -1;
        if (slot < 0) load(slot = 1 - recent, picture);
        recent = slot;
        if (textures[slot] == null) return false;
        int[] part = CrosshairBackground.cover(widths[slot], heights[slot], w, h);
        Minecraft.getMinecraft().getTextureManager().bindTexture(textures[slot]);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR); // drawn scaled
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1, 1, 1, 1);
        Gui.drawScaledCustomSizeModalRect(x, y, part[0], part[1], part[2], part[3], w, h, widths[slot], heights[slot]);
        return true;
    }

    private static void load(int slot, String picture) {
        sources[slot] = picture;
        if (textures[slot] != null) Minecraft.getMinecraft().getTextureManager().deleteTexture(textures[slot]);
        textures[slot] = null;
        CrosshairBackground.Picture p = CrosshairBackground.read(picture);
        if (p == null) return;
        DynamicTexture dynamic = new DynamicTexture(p.width, p.height);
        int[] data = dynamic.getTextureData();
        System.arraycopy(p.argb, 0, data, 0, data.length);
        dynamic.updateDynamicTexture();
        textures[slot] = Minecraft.getMinecraft().getTextureManager().getDynamicTextureLocation("lads_picture_" + slot, dynamic);
        widths[slot] = p.width;
        heights[slot] = p.height;
    }
}
