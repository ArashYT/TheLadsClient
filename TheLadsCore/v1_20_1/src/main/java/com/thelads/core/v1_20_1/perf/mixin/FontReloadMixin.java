package com.thelads.core.v1_20_1.perf.mixin;

import com.thelads.core.perf.FontWidthCache;
import com.thelads.core.v1_20_1.adapter.GuiGraphicsLadsAdapter1201;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Clears the global font-width cache whenever font resources are reloaded, or Force Unicode Font swaps the default font.
 */
@Mixin(FontManager.class)
public abstract class FontReloadMixin {
    @Inject(method = "reload", at = @At("RETURN"), require = 1)
    private void lads$clearFontCache(CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        cir.getReturnValue().thenRun(FontWidthCache::clear);
    }

    /** Force Unicode Font (Minecraft.selectMainFont) renames the default font here, without a resource reload. */
    @Inject(method = "setRenames", at = @At("RETURN"), require = 1)
    private void lads$clearOnRename(Map<ResourceLocation, ResourceLocation> renames, CallbackInfo ci) {
        FontWidthCache.clear();
        GuiGraphicsLadsAdapter1201.invalidateMetrics();
    }
}
