package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.FontCache189;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.IResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The string width cache (FontCache189). OptiFine replaces this class (HD fonts, custom colours); these hooks sit on methods it
 * keeps. Injected at HEAD only (Mixin 0.7); the hook calls the original method again with its flag set to measure for real.
 */
@Mixin(FontRenderer.class)
public abstract class FontRendererMixin implements FontCache189.Holder {
    @Unique private FontCache189 ladsPlain, ladsUnicode;
    @Unique private boolean ladsMeasuring;

    @Shadow public abstract int getStringWidth(String text);

    @Override
    public FontCache189 ladsCache() {
        FontRenderer font = (FontRenderer) (Object) this;
        if (font.getUnicodeFlag()) return ladsUnicode != null ? ladsUnicode : (ladsUnicode = new FontCache189(font));
        return ladsPlain != null ? ladsPlain : (ladsPlain = new FontCache189(font));
    }

    @Inject(method = "getStringWidth", at = @At("HEAD"), cancellable = true)
    private void ladsWidth(String text, CallbackInfoReturnable<Integer> cir) {
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

    @Inject(method = "onResourceManagerReload", at = @At("HEAD"))
    private void ladsReload(IResourceManager manager, CallbackInfo ci) {
        if (ladsPlain != null) ladsPlain.clear();
        if (ladsUnicode != null) ladsUnicode.clear();
    }
}
