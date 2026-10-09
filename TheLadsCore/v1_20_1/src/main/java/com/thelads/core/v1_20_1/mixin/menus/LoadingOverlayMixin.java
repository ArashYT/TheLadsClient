package com.thelads.core.v1_20_1.mixin.menus;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.thelads.core.client.gui.LadsPalette;
import com.thelads.core.v1_20_1.gui.LoadingLogo1201;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Lads loading screen instead of Mojang Studios' (startup and every resource reload). Vanilla keeps its timing, fades
 * and reload callbacks; only its logo, red background and bar are swapped. Priority 1100 draws over Drippy's layouts.
 * (Forge's own ForgeLoadingOverlay is handled by ForgeLoadingOverlayMixin.)
 */
@Mixin(value = LoadingOverlay.class, priority = 1100)
public abstract class LoadingOverlayMixin {
    @Shadow @Final private boolean fadeIn;
    @Shadow private float currentProgress;
    @Shadow private long fadeOutStart;
    @Shadow private long fadeInStart;

    @ModifyExpressionValue(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
        at = @At(value = "INVOKE", target = "Ljava/util/function/IntSupplier;getAsInt()I"), require = 1)
    private int ladsBackground(int brand) {
        return LadsPalette.BACKGROUND;
    }

    @WrapWithCondition(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIFFIIII)V"), require = 1)
    private boolean ladsHideMojangLogo(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width, int height,
                                       float u, float v, int uWidth, int vHeight, int textureWidth, int textureHeight) {
        return false;
    }

    @Inject(method = "drawProgressBar", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsHideVanillaBar(GuiGraphics graphics, int minX, int minY, int maxX, int maxY, float opacity, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("RETURN"), require = 1)
    private void ladsRender(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        long now = Util.getMillis();
        float out = fadeOutStart > -1 ? (now - fadeOutStart) / 1000f : -1, in = fadeInStart > -1 ? (now - fadeInStart) / 500f : -1;
        float alpha = out >= 1 ? 1 - Mth.clamp(out - 1, 0, 1) : fadeIn ? Mth.clamp(in, 0, 1) : 1;
        LoadingLogo1201.draw(graphics, currentProgress, alpha, 1 - Mth.clamp(out, 0, 1));
    }
}
