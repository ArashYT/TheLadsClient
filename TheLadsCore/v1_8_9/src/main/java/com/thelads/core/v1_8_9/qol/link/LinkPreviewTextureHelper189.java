package com.thelads.core.v1_8_9.qol.link;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;

public final class LinkPreviewTextureHelper189 {
    private static ResourceLocation activeLocation = null;

    private LinkPreviewTextureHelper189() {}

    public static synchronized void setActive(ResourceLocation loc) {
        releaseActive();
        activeLocation = loc;
    }

    public static synchronized void releaseActive() {
        if (activeLocation != null) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null && mc.getTextureManager() != null) {
                mc.getTextureManager().deleteTexture(activeLocation);
            }
            activeLocation = null;
        }
    }
}
