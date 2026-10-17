package com.thelads.core.v1_20_1.game.mixin;

import java.util.Arrays;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.AccessibilityOptionsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The Damage Tilt module replaces Minecraft's Damage Tilt slider (DamageTiltMixin ignores its value), so Accessibility drops it. */
@Mixin(AccessibilityOptionsScreen.class)
public class NoVanillaDamageTiltMixin {
    @Inject(method = "options", at = @At("RETURN"), cancellable = true)
    private static void lads$noDamageTilt(Options options, CallbackInfoReturnable<OptionInstance<?>[]> cir) {
        OptionInstance<?> tilt = options.damageTiltStrength();
        cir.setReturnValue(Arrays.stream(cir.getReturnValue()).filter(o -> o != tilt).toArray(OptionInstance<?>[]::new));
    }
}
