/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.Font
 *  net.minecraft.client.gui.screens.LoadingOverlay
 *  net.minecraft.util.Mth
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.Redirect
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.thelads.core.mixin;

import java.util.function.IntSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={LoadingOverlay.class})
public class LoadingOverlayMixin {
    @Shadow
    @Final
    private Minecraft minecraft;
    @Shadow
    private float currentProgress;

    @Redirect(method={"extractRenderState"}, at=@At(value="INVOKE", target="Ljava/util/function/IntSupplier;getAsInt()I"), require=0)
    private int redirectBrandBackground(IntSupplier supplier) {
        return 657935;
    }

    @Inject(method={"extractRenderState"}, at={@At(value="TAIL")}, require=0)
    private void onExtractRenderStateTail(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        int width = this.minecraft.getWindow().getGuiScaledWidth();
        int height = this.minecraft.getWindow().getGuiScaledHeight();
        g.fill(0, 0, width, height, -16119281);
        Font font = this.minecraft.font;
        long now = System.currentTimeMillis();
        float prog = Mth.clamp((float)this.currentProgress, (float)0.0f, (float)1.0f);
        float gridOff = (float)(now % 4000L) / 4000.0f * 32.0f;
        for (int gx = -((int)gridOff); gx < width + 32; gx += 32) {
            g.fill(gx, 0, gx + 1, height, 0x8FFFFFF);
        }
        for (int gy = 0; gy < height; gy += 32) {
            g.fill(0, gy, width, gy + 1, 0x8FFFFFF);
        }
        float glowPulse = 0.5f + 0.5f * (float)Math.sin((double)now / 600.0);
        int glowAlpha = (int)(60.0f * glowPulse) & 0xFF;
        int cx = width / 2;
        int cy = height / 2;
        int ringR = 48;
        for (int r = ringR + 6; r >= ringR; --r) {
            int a = (int)((float)glowAlpha * (1.0f - (float)(r - ringR) / 6.0f)) & 0xFF;
            if (a <= 0) continue;
            LoadingOverlayMixin.drawCircleOutline(g, cx, cy, r, a << 24 | 0x6C63FF);
        }
        float breathe = 0.85f + 0.15f * (float)Math.sin((double)now / 800.0);
        int titleAlpha = (int)(255.0f * breathe) & 0xFF;
        int titleColor = titleAlpha << 24 | 0x8C83FF;
        g.centeredText(font, "THE LADS CLIENT", cx, cy + 28, titleColor);
        int barW = 240;
        int barH = 5;
        int barX = cx - barW / 2;
        int barY = cy + 50;
        g.fill(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, -15066578);
        g.fill(barX, barY, barX + barW, barY + barH, -14671814);
        int fillW = (int)((float)barW * prog);
        if (fillW > 0) {
            g.fill(barX, barY, barX + fillW, barY + barH, -10726162);
            int edgeX = barX + fillW - 2;
            if (edgeX >= barX) {
                g.fill(edgeX, barY, barX + fillW, barY + barH, -5330177);
            }
            float shimmerT = (float)(now % 1200L) / 1200.0f;
            int shimCx = barX + (int)((float)fillW * shimmerT);
            int shimHalf = 14;
            for (int sx = Math.max(barX, shimCx - shimHalf); sx < Math.min(barX + fillW, shimCx + shimHalf); ++sx) {
                float d = 1.0f - (float)Math.abs(sx - shimCx) / (float)shimHalf;
                int sAlpha = (int)(120.0f * d * d) & 0xFF;
                g.fill(sx, barY, sx + 1, barY + barH, sAlpha << 24 | 0xFFFFFF);
            }
        }
        String pctText = (int)(prog * 100.0f) + "%";
        g.centeredText(font, pctText, cx, barY + barH + 5, -1140850689);
        int dotCount = 5;
        int dotSpacing = 10;
        int dotStartX = cx - dotCount * dotSpacing / 2;
        int dotY = barY + barH + 20;
        long cycleMs = 700L;
        long phase = now % (cycleMs * (long)dotCount);
        for (int i = 0; i < dotCount; ++i) {
            float t = ((float)phase - (float)((long)i * cycleMs) / (float)dotCount) / (float)cycleMs % 1.0f;
            if (t < 0.0f) {
                t += 1.0f;
            }
            float bright = t < 0.5f ? t * 2.0f : 1.0f - (t - 0.5f) * 2.0f;
            int dotAlpha = (int)(80.0f + 175.0f * bright) & 0xFF;
            int dotColor = dotAlpha << 24 | 0x8C83FF;
            int dx = dotStartX + i * dotSpacing;
            g.fill(dx - 2, dotY - 2, dx + 2, dotY + 2, dotColor);
        }
    }

    private static void drawCircleOutline(GuiGraphicsExtractor g, int cx, int cy, int r, int color) {
        int rSq = r * r;
        int rInSq = (r - 1) * (r - 1);
        for (int dy = -r; dy <= r; ++dy) {
            for (int dx = -r; dx <= r; ++dx) {
                int d = dx * dx + dy * dy;
                if (d > rSq || d <= rInSq) continue;
                g.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
            }
        }
    }
}

