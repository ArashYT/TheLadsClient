package com.thelads.core.v1_21_1.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.thelads.core.client.gui.LadsPalette;
import com.thelads.core.client.title.LoadingScreenTheme;
import com.thelads.core.v1_21_1.adapter.GuiGraphicsLadsAdapter;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.InputStream;

/**
 * The Lads loading screen instead of Mojang Studios' (startup and every resource reload). Vanilla keeps its timing, fades
 * and reload callbacks; only its logo, red background and bar are swapped. Priority 1100 draws over Drippy's layouts.
 */
@Mixin(value = LoadingOverlay.class, priority = 1100)
public abstract class LoadingOverlayMixin {
    @Unique private static final ResourceLocation LADS_LOGO = ResourceLocation.fromNamespaceAndPath("theladscore", "loading_logo");
    @Unique private static boolean ladsLogoTried, ladsLogoReady;
    @Shadow @Final private boolean fadeIn;
    @Shadow private float currentProgress;
    @Shadow private long fadeOutStart;
    @Shadow private long fadeInStart;

    @ModifyExpressionValue(method = "render", at = @At(value = "INVOKE", target = "Ljava/util/function/IntSupplier;getAsInt()I"), require = 1)
    private int ladsBackground(int brand) {
        return LadsPalette.BACKGROUND;
    }

    @WrapWithCondition(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIFFIIII)V"), require = 1)
    private boolean ladsHideMojangLogo(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width, int height,
                                       float u, float v, int uWidth, int vHeight, int textureWidth, int textureHeight) {
        return false;
    }

    @Inject(method = "drawProgressBar", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsHideVanillaBar(GuiGraphics graphics, int minX, int minY, int maxX, int maxY, float partialTick, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "render", at = @At("RETURN"), require = 1)
    private void ladsRender(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        long now = Util.getMillis();
        float out = fadeOutStart > -1 ? (now - fadeOutStart) / 1000f : -1, in = fadeInStart > -1 ? (now - fadeInStart) / 500f : -1;
        float alpha = out >= 1 ? 1 - Mth.clamp(out - 1, 0, 1) : fadeIn ? Mth.clamp(in, 0, 1) : 1;
        LoadingScreenTheme.render(new GuiGraphicsLadsAdapter(graphics, Minecraft.getInstance().font), graphics.guiWidth(), graphics.guiHeight(),
            currentProgress, alpha, 1 - Mth.clamp(out, 0, 1), (x, y, width, height, a) -> {
                if (!ladsLogo()) return;
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                graphics.setColor(1, 1, 1, a);
                graphics.blit(LADS_LOGO, x, y, width, height, 0, 0, LoadingScreenTheme.LOGO_WIDTH, LoadingScreenTheme.LOGO_HEIGHT,
                    LoadingScreenTheme.LOGO_WIDTH, LoadingScreenTheme.LOGO_HEIGHT);
                graphics.setColor(1, 1, 1, 1);
                RenderSystem.disableBlend();
            });
    }

    @Unique
    private static boolean ladsLogo() {
        if (ladsLogoTried) return ladsLogoReady;
        ladsLogoTried = true;
        try (InputStream in = LoadingScreenTheme.class.getResourceAsStream(LoadingScreenTheme.LOGO)) {
            if (in == null) throw new IllegalStateException(LoadingScreenTheme.LOGO + " is missing");
            DynamicTexture texture = new DynamicTexture(NativeImage.read(in));
            texture.setFilter(true, false);
            Minecraft.getInstance().getTextureManager().register(LADS_LOGO, texture);
            ladsLogoReady = true;
        } catch (Exception failure) {
            LoggerFactory.getLogger("TheLadsCore").error("Lads loading screen logo unavailable", failure);
        }
        return ladsLogoReady;
    }
}
