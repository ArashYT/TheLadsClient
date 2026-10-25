package com.thelads.core.v1_20_1.qol.link;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

public final class LinkPreviewTextureHelper1201 {
    private static ResourceLocation activeLocation = null;
    private static DynamicTexture activeTexture = null;

    private LinkPreviewTextureHelper1201() {}

    public static synchronized void setActive(ResourceLocation loc, DynamicTexture tex) {
        releaseActive();
        activeLocation = loc;
        activeTexture = tex;
    }

    public static synchronized void releaseActive() {
        if (activeTexture != null) {
            try {
                activeTexture.close();
            } catch (Throwable ignored) {}
            activeTexture = null;
        }
        if (activeLocation != null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getTextureManager() != null) {
                mc.getTextureManager().release(activeLocation);
            }
            activeLocation = null;
        }
    }
}
