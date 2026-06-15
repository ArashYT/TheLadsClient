/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.renderer.RenderPipelines
 */
package com.thelads.core.modules.killbanner;

import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.KillBannerModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public class KillBannerRenderer {
    private static final Identifier BASE_BANNER = Identifier.fromNamespaceAndPath("theladscore", "textures/gui/base_kill_banner.png");
    private static final Identifier REAVER_BANNER = Identifier.fromNamespaceAndPath("theladscore", "textures/gui/reaver_kill_banner.png");
    private static long killTriggerTime = -1L;
    private static final long ANIMATION_DURATION = 2000L;

    public static void triggerKill() {
        killTriggerTime = System.currentTimeMillis();
    }

    public static void extract(GuiGraphicsExtractor extractor) {
        Module mod = ModuleManager.getInstance().getModule("KillBanner");
        if (mod == null || !mod.isEnabled()) {
            return;
        }
        if (killTriggerTime == -1L) {
            return;
        }
        long elapsed = System.currentTimeMillis() - killTriggerTime;
        if (elapsed > 2000L) {
            killTriggerTime = -1L;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        int centerX = width / 2;
        int bottomY = height - 80;
        float progress = (float)elapsed / 2000.0f;
        float scale = 1.0f;
        if (progress < 0.1f) {
            p = progress / 0.1f;
            scale = 0.5f + 0.5f * p;
        } else if (progress > 0.8f) {
            p = (progress - 0.8f) / 0.2f;
            scale = Math.max(0.0f, 1.0f - p);
        }
        if (scale <= 0.0f) {
            return;
        }
        int targetSize = (int)(80.0f * scale);
        int x = centerX - targetSize / 2;
        int y = bottomY - targetSize;
        Identifier activeBanner = BASE_BANNER;
        int style = 0;
        if (mod instanceof KillBannerModule && (style = ((KillBannerModule)mod).bannerStyle.getIndex()) == 1) {
            activeBanner = REAVER_BANNER;
        }
        extractor.blit(RenderPipelines.GUI_TEXTURED, activeBanner, x, y, 0.0f, 0.0f, targetSize, targetSize, style == 1 ? 300 : 312, style == 1 ? 300 : 400, style == 1 ? 300 : 312, style == 1 ? 300 : 400);
    }
}

