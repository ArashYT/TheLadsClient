package com.thelads.core.mixin;

import net.minecraft.client.Minecraft;
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

import java.util.function.IntSupplier;

@Mixin(LoadingOverlay.class)
public class LoadingOverlayMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow private float currentProgress;

    @Redirect(
        method = "extractRenderState",
        at = @At(value = "INVOKE", target = "Ljava/util/function/IntSupplier;getAsInt()I"),
        require = 0
    )
    private int redirectBrandBackground(IntSupplier supplier) {
        return 0x0F0A0A;
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
    private void onExtractRenderStateTail(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        int width  = this.minecraft.getWindow().getGuiScaledWidth();
        int height = this.minecraft.getWindow().getGuiScaledHeight();

        // Blank out anything vanilla drew (Mojang Studios logo, vanilla progress bar)
        // by filling the full screen with our background color. Draws are ordered, so
        // this appears on top of vanilla renders from earlier in the same method.
        g.fill(0, 0, width, height, 0xFF000000);
        var font   = this.minecraft.font;
        long now   = System.currentTimeMillis();
        float prog = Mth.clamp(this.currentProgress, 0.0f, 1.0f);

        // ── subtle moving grid pattern ──────────────────────────────────────
        float gridOff = (now % 4000) / 4000.0f * 32;
        for (int gx = -(int)gridOff; gx < width + 32; gx += 32) {
            g.fill(gx, 0, gx + 1, height, 0x08FFFFFF);
        }
        for (int gy = 0; gy < height; gy += 32) {
            g.fill(0, gy, width, gy + 1, 0x08FFFFFF);
        }

        // ── logo glow ring (pulsing circle behind title) ─────────────────────
        float glowPulse = 0.5f + 0.5f * (float) Math.sin(now / 600.0);
        int glowAlpha   = (int)(60 * glowPulse) & 0xFF;
        int cx = width / 2, cy = height / 2;
        int ringR = 48;
        for (int r = ringR + 6; r >= ringR; r--) {
            int a = (int)(glowAlpha * (1.0f - (float)(r - ringR) / 6.0f)) & 0xFF;
            if (a > 0) drawCircleOutline(g, cx, cy, r, (a << 24) | 0x990000);
        }

        // ── title ────────────────────────────────────────────────────────────
        float breathe   = 0.85f + 0.15f * (float) Math.sin(now / 800.0);
        int titleAlpha  = (int)(255 * breathe) & 0xFF;
        int titleColor  = (titleAlpha << 24) | 0xFF3333;
        g.centeredText(font, "THE LADS CLIENT", cx, cy + 28, titleColor);

        // ── progress bar ─────────────────────────────────────────────────────
        int barW = 240, barH = 5;
        int barX = cx - barW / 2;
        int barY = cy + 50;

        // track + border
        g.fill(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, 0xFF1A0A0A);
        g.fill(barX, barY, barX + barW, barY + barH, 0xFF2A0D0D);

        // filled portion with gradient-like edge
        int fillW = (int)(barW * prog);
        if (fillW > 0) {
            g.fill(barX, barY, barX + fillW, barY + barH, 0xFF990000);

            // brighter leading edge (2px)
            int edgeX = barX + fillW - 2;
            if (edgeX >= barX) {
                g.fill(edgeX, barY, barX + fillW, barY + barH, 0xFFFF3333);
            }

            // sweeping shimmer
            float shimmerT = (float)(now % 1200) / 1200.0f;
            int shimCx     = barX + (int)(fillW * shimmerT);
            int shimHalf   = 14;
            for (int sx = Math.max(barX, shimCx - shimHalf); sx < Math.min(barX + fillW, shimCx + shimHalf); sx++) {
                float d    = 1.0f - Math.abs(sx - shimCx) / (float) shimHalf;
                int sAlpha = (int)(120 * d * d) & 0xFF;
                g.fill(sx, barY, sx + 1, barY + barH, (sAlpha << 24) | 0xFFFFFF);
            }
        }

        // ── percentage label ─────────────────────────────────────────────────
        String pctText = (int)(prog * 100) + "%";
        g.centeredText(font, pctText, cx, barY + barH + 5, 0xBBFFFFFF);

        // ── animated spinner dots ─────────────────────────────────────────────
        int dotCount   = 5;
        int dotSpacing = 10;
        int dotStartX  = cx - (dotCount * dotSpacing) / 2;
        int dotY       = barY + barH + 20;
        long cycleMs   = 700L;
        long phase     = now % (cycleMs * dotCount);
        for (int i = 0; i < dotCount; i++) {
            float t = ((phase - i * cycleMs / (float) dotCount) / cycleMs) % 1.0f;
            if (t < 0) t += 1.0f;
            float bright   = t < 0.5f ? (t * 2.0f) : (1.0f - (t - 0.5f) * 2.0f);
            int dotAlpha   = (int)(80 + 175 * bright) & 0xFF;
            int dotColor   = (dotAlpha << 24) | 0x990000;
            int dx         = dotStartX + i * dotSpacing;
            g.fill(dx - 2, dotY - 2, dx + 2, dotY + 2, dotColor);
        }
    }

    private static void drawCircleOutline(GuiGraphicsExtractor g, int cx, int cy, int r, int color) {
        int rSq   = r * r;
        int rInSq = (r - 1) * (r - 1);
        for (int dy = -r; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                int d = dx * dx + dy * dy;
                if (d <= rSq && d > rInSq) {
                    g.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
                }
            }
        }
    }
}
