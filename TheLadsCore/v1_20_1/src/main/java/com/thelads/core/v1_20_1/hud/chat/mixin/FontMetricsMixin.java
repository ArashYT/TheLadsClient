package com.thelads.core.v1_20_1.hud.chat.mixin;

import com.thelads.core.v1_20_1.adapter.GuiGraphicsLadsAdapter1201;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.gui.font.FontManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Text widths cached by the HUD are dropped when a resource reload changes the fonts. */
@Mixin(FontManager.class)
public abstract class FontMetricsMixin {
    @Inject(method = "reload", at = @At("RETURN"), require = 1)
    private void lads$metrics(CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        cir.getReturnValue().thenRun(GuiGraphicsLadsAdapter1201::invalidateMetrics);
    }
}
