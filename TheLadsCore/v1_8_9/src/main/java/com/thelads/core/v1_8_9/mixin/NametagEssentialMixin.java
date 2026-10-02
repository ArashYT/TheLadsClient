package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.Nametags189;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Nametags background behind Essential's icon. On 1.8.9 Essential pads every name tag (and backs its icon) with its own quad,
 * drawn with this fixed opacity (64) rather than vanilla's background, so it is cleared here with Render Background off.
 * Essential is downloaded at runtime: @Pseudo skips a missing class, and require = 0 a changed method.
 */
@Pseudo
@Mixin(targets = "gg.essential.handlers.OnlineIndicator", remap = false)
public abstract class NametagEssentialMixin {
    @Inject(method = "getTextBackgroundOpacity", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void ladsBackground(CallbackInfoReturnable<Integer> cir) {
        if (!Nametags189.background()) cir.setReturnValue(0);
    }
}
