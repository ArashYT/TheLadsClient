package com.thelads.core.v1_20_1.perf.mixin;

import com.thelads.core.perf.FontWidthCache;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.gui.font.FontManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Clears the global font-width cache whenever font resources are reloaded.
 */
@Mixin(FontManager.class)
public abstract class FontReloadMixin {
    @Inject(method = "reload", at = @At("RETURN"), require = 1)
    private void lads$clearFontCache(CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        cir.getReturnValue().thenRun(FontWidthCache::clear);
    }
}
