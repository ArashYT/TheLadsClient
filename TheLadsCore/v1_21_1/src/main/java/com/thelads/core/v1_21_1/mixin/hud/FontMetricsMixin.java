package com.thelads.core.v1_21_1.mixin.hud;

import com.thelads.core.v1_21_1.adapter.GuiGraphicsLadsAdapter;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.gui.font.FontManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Cached HUD text widths are measured again after a font or resource-pack reload. */
@Mixin(FontManager.class)
public abstract class FontMetricsMixin {
    @Inject(method = "reload", at = @At("RETURN"))
    private void ladsMetrics(CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        cir.getReturnValue().thenRun(GuiGraphicsLadsAdapter::invalidateMetrics);
    }
}
