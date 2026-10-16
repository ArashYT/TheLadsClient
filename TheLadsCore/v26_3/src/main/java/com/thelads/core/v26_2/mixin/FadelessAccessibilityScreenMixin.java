package com.thelads.core.v26_2.mixin;

import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Consolidates Fadeless: removes fade-in/fade-out delay on AccessibilityOnboardingScreen.
 */
@Mixin(AccessibilityOnboardingScreen.class)
public abstract class FadelessAccessibilityScreenMixin {
    @Redirect(
            method = "extractRenderState",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Util;getMillis()J", ordinal = 0),
            require = 0
    )
    private long lads$skipOnboardingFadeInStart() {
        return 0L;
    }

    @Redirect(
            method = "extractRenderState",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Util;getMillis()J", ordinal = 1),
            require = 0
    )
    private long lads$skipOnboardingFadeInCurrent() {
        return 0L;
    }

    @Redirect(
            method = "extractRenderState",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Util;getMillis()J", ordinal = 2),
            require = 0
    )
    private long lads$skipOnboardingFadeOutEnd() {
        return Long.MAX_VALUE;
    }
}
