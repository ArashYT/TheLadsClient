package com.thelads.core.v26_2.mixin;

import com.thelads.core.perf.FontWidthCache;
import com.thelads.core.v26_2.adapter.GuiGraphicsExtractorLadsAdapter;
import net.minecraft.client.Options;
import net.minecraft.client.gui.font.FontManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.concurrent.CompletableFuture;

@Mixin(FontManager.class)
public abstract class FontMetricsMixin {
    @Inject(method = "reload", at = @At("RETURN"), require = 1)
    private void ladsMetrics(CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        cir.getReturnValue().thenRun(() -> {
            GuiGraphicsExtractorLadsAdapter.invalidateMetrics();
            FontWidthCache.clear();
        });
    }

    /** Force Unicode Font and Japanese glyph variants swap glyphs here, without a resource reload. */
    @Inject(method = "updateOptions", at = @At("RETURN"), require = 1)
    private void ladsFontOptions(Options options, CallbackInfo ci) {
        GuiGraphicsExtractorLadsAdapter.invalidateMetrics();
        FontWidthCache.clear();
    }
}
