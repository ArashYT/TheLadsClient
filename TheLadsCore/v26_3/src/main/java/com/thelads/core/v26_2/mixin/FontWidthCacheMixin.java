package com.thelads.core.v26_2.mixin;

import com.thelads.core.perf.FontWidthCache;
import net.minecraft.client.gui.Font;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Global text-width cache (FontWidthCache; 26.3 parity with Polydium).
 * Intercepts string width calculations to eliminate repetitive layout cost across HUD, scoreboard, chat, and menus.
 */
@Mixin(Font.class)
public abstract class FontWidthCacheMixin {
    @Inject(method = "width(Ljava/lang/String;)I", at = @At("HEAD"), cancellable = true)
    private void lads$cachedWidth(String text, CallbackInfoReturnable<Integer> cir) {
        if (!FontWidthCache.enabled || text == null) return;
        Integer cached = FontWidthCache.get(text);
        if (cached != null) cir.setReturnValue(cached);
    }

    @Inject(method = "width(Ljava/lang/String;)I", at = @At("RETURN"))
    private void lads$storeWidth(String text, CallbackInfoReturnable<Integer> cir) {
        if (cir.isCancelled() || !FontWidthCache.enabled || text == null) return;
        Integer val = cir.getReturnValue();
        if (val != null) {
            FontWidthCache.put(text, val);
        }
    }
}
