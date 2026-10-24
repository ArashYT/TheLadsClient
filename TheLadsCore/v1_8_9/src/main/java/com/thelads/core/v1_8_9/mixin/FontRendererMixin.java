package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.FontCache189;
import com.thelads.core.v1_8_9.feature.FontShadow189;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.IResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The string width cache (FontCache189) and shadow-only draw pass (FontShadow189). OptiFine replaces this class (HD fonts,
 * custom colours); these hooks sit on methods it keeps. Injected at HEAD only (Mixin 0.7); the hook calls the original method
 * again with its flag set to measure for real.
 */
@Mixin(FontRenderer.class)
public abstract class FontRendererMixin implements FontCache189.Holder, FontShadow189 {
    @Unique private FontCache189 ladsPlain, ladsUnicode;
    @Unique private boolean ladsMeasuring;

    @Shadow public abstract int getStringWidth(String text);
    @Shadow protected abstract void resetStyles();
    @Shadow private int renderString(String text, float x, float y, int color, boolean dropShadow) { return 0; }

    @Inject(method = "renderString", at = @At("HEAD"), cancellable = true)
    private void ladsTrueTypeRender(String text, float x, float y, int color, boolean dropShadow, CallbackInfoReturnable<Integer> cir) {
        if (com.thelads.core.client.TrueTypeFont.isEnabled()) {
            cir.setReturnValue(com.thelads.core.client.TrueTypeFont.drawString(text, x, y, color, dropShadow));
        }
    }

    @Override
    public int ladsDrawShadow(String text, float x, float y, int color) {
        if (com.thelads.core.client.TrueTypeFont.isEnabled()) {
            return com.thelads.core.client.TrueTypeFont.drawString(text, x, y, color, true);
        }
        if (text == null) return 0;
        GlStateManager.enableAlpha();
        resetStyles();
        return renderString(text, x + 1.0F, y + 1.0F, color, true);
    }

    @Override
    public FontCache189 ladsCache() {
        FontRenderer font = (FontRenderer) (Object) this;
        if (font.getUnicodeFlag()) return ladsUnicode != null ? ladsUnicode : (ladsUnicode = new FontCache189(font));
        return ladsPlain != null ? ladsPlain : (ladsPlain = new FontCache189(font));
    }

    @Inject(method = "getStringWidth", at = @At("HEAD"), cancellable = true)
    private void ladsWidth(String text, CallbackInfoReturnable<Integer> cir) {
        if (com.thelads.core.client.TrueTypeFont.isEnabled()) { cir.setReturnValue(com.thelads.core.client.TrueTypeFont.getStringWidth(text)); return; }
        if (ladsMeasuring) return;
        FontCache189 cache = ladsCache();
        if (text == null || !cache.on()) return;
        int width = cache.width(text);
        if (width < 0) {
            ladsMeasuring = true;
            try { width = getStringWidth(text); } finally { ladsMeasuring = false; }
            cache.putWidth(text, width);
        }
        cir.setReturnValue(width);
    }

    @Inject(method = "onResourceManagerReload", at = @At("HEAD"), cancellable = true)
    private void ladsReload(IResourceManager manager, CallbackInfo ci) {
        if (com.thelads.core.client.pack.PackAssetMask.shouldSkipFonts()) {
            ci.cancel();
            return;
        }
        if (ladsPlain != null) ladsPlain.clear();
        if (ladsUnicode != null) ladsUnicode.clear();
    }

    @Unique private static int[] ladsCharIndices;

    @Redirect(method = "getCharWidth", at = @At(value = "INVOKE", target = "Ljava/lang/String;indexOf(I)I", remap = false), require = 0)
    private int ladsFastCharWidthIndexOf(String s, int ch) {
        int[] table = ladsCharIndices;
        if (table == null) {
            table = ladsCharIndices = new int[256];
            for (int i = 0; i < 256; i++) {
                table[i] = s.indexOf(i);
            }
        }
        return (ch >= 0 && ch < 256) ? table[ch] : s.indexOf(ch);
    }
}
