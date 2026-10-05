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
 * The text cache (FontCache189): string widths and display lists of drawn strings. OptiFine replaces this class (HD fonts,
 * custom colours); these hooks sit on methods it keeps, and the cache records whatever its renderString draws. Injected at HEAD
 * only (Mixin 0.7); a hook calls the original method again with its flag set to measure or draw for real.
 */
@Mixin(FontRenderer.class)
public abstract class FontRendererMixin implements FontCache189.Holder, FontCache189.Draw {
    @Shadow protected float posX;
    @Shadow protected float posY;
    @Unique private FontCache189 ladsCache;
    @Unique private boolean ladsMeasuring, ladsDrawing;

    @Shadow public abstract int getStringWidth(String text);
    @Shadow private int renderString(String text, float x, float y, int color, boolean dropShadow) { return 0; }

    @Override
    public FontCache189 ladsCache() {
        if (ladsCache == null) ladsCache = new FontCache189((FontRenderer) (Object) this);
        return ladsCache;
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

    @Inject(method = "renderString", at = @At("HEAD"), cancellable = true)
    private void ladsDraw(String text, float x, float y, int color, boolean dropShadow, CallbackInfoReturnable<Integer> cir) {
        if (ladsDrawing) return;
        FontCache189 cache = ladsCache();
        if (!cache.cacheable(text)) return;
        float advance = cache.render(text, x, y, color, dropShadow, this);
        if (Float.isNaN(advance)) return;
        posX = x + advance;
        posY = y;
        cir.setReturnValue((int) posX);
    }

    @Override
    public float ladsDrawAtOrigin(String text, int color, boolean dropShadow) {
        ladsDrawing = true;
        try {
            renderString(text, 0, 0, color, dropShadow);
            return posX;
        } finally {
            ladsDrawing = false;
        }
    }

    @Inject(method = "onResourceManagerReload", at = @At("HEAD"))
    private void ladsReload(IResourceManager manager, CallbackInfo ci) {
        ladsCache().clear();
    }
}
