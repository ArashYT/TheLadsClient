package com.thelads.core.v26_2.qol.link;

import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

public final class LinkPreviewTextureHelper26 {
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static Identifier activeLocation = null;
    private static DynamicTexture activeTexture = null;

    private LinkPreviewTextureHelper26() {}

    public static Identifier nextLocation() {
        return Identifier.fromNamespaceAndPath("theladscore", "link_preview_" + COUNTER.incrementAndGet());
    }

    public static synchronized void setActive(Identifier loc, DynamicTexture tex) {
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
