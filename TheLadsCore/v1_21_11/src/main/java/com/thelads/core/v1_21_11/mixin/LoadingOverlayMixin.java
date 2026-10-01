package com.thelads.core.v1_21_11.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.thelads.core.client.gui.LadsPalette;
import com.thelads.core.client.title.LoadingScreenTheme;
import com.thelads.core.v1_21_11.adapter.GuiGraphicsLadsAdapter;
import com.thelads.core.v1_21_11.gui.LoadingLogo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Lads loading screen instead of Mojang Studios' (startup and every resource reload). Vanilla keeps its timing, fades
 * and reload callbacks; only its logo, red background and bar are swapped. Priority 1100 draws over Drippy's layouts.
 */
@Mixin(value = LoadingOverlay.class, priority = 1100)
public abstract class LoadingOverlayMixin {
    @Shadow @Final private boolean fadeIn;
    @Shadow private float currentProgress;
    @Shadow private long fadeOutStart;
    @Shadow private long fadeInStart;

    @ModifyExpressionValue(method = "render", at = @At(value = "INVOKE", target = "Ljava/util/function/IntSupplier;getAsInt()I"), require = 1)
    private int ladsBackground(int brand) {
        return LadsPalette.BACKGROUND;
    }

    @WrapWithCondition(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V"), require = 1)
    private boolean ladsHideMojangLogo(GuiGraphics graphics, RenderPipeline pipeline, Identifier texture, int x, int y, float u, float v,
                                       int width, int height, int uWidth, int vHeight, int textureWidth, int textureHeight, int color) {
        return false;
    }

    @Inject(method = "drawProgressBar", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsHideVanillaBar(GuiGraphics graphics, int minX, int minY, int maxX, int maxY, float opacity, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "render", at = @At("RETURN"), require = 1)
    private void ladsRender(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        long now = Util.getMillis();
        float out = fadeOutStart > -1 ? (now - fadeOutStart) / 1000f : -1, in = fadeInStart > -1 ? (now - fadeInStart) / 500f : -1;
        float alpha = out >= 1 ? 1 - Mth.clamp(out - 1, 0, 1) : fadeIn ? Mth.clamp(in, 0, 1) : 1;
        LoadingScreenTheme.render(new GuiGraphicsLadsAdapter(graphics, Minecraft.getInstance().font), graphics.guiWidth(), graphics.guiHeight(),
            currentProgress, alpha, 1 - Mth.clamp(out, 0, 1), (x, y, width, height, a) -> {
                if (LoadingLogo.ready()) graphics.blit(RenderPipelines.GUI_TEXTURED, LoadingLogo.ID, x, y, 0, 0, width, height,
                    LoadingScreenTheme.LOGO_WIDTH, LoadingScreenTheme.LOGO_HEIGHT, LoadingScreenTheme.LOGO_WIDTH, LoadingScreenTheme.LOGO_HEIGHT, ARGB.white(a));
            });
    }
}
