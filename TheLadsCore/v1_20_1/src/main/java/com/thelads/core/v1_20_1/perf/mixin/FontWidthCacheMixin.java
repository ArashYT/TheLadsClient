package com.thelads.core.v1_20_1.perf.mixin;

import com.thelads.core.perf.FontWidthCache;
import net.minecraft.client.gui.Font;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Global text-width cache (FontWidthCache; 1.20.1 parity with 1.8.9 FontCache189 and Polydium).
 * Intercepts string width calculations to eliminate repetitive layout cost across HUD, scoreboard, chat, and menus.
 */
@Mixin(Font.class)
public abstract class FontWidthCacheMixin {
    @Inject(method = "width(Ljava/lang/String;)I", at = @At("HEAD"), cancellable = true)
    private void lads$cachedWidth(String text, CallbackInfoReturnable<Integer> cir) {
        if (text == null) return;
        int cached = FontWidthCache.get(text);
        if (cached >= 0) cir.setReturnValue(cached);
    }

    @Inject(method = "width(Ljava/lang/String;)I", at = @At("RETURN"))
    private void lads$storeWidth(String text, CallbackInfoReturnable<Integer> cir) {
        if (text != null && cir.getReturnValue() != null) {
            FontWidthCache.put(text, cir.getReturnValue());
        }
    }
}
