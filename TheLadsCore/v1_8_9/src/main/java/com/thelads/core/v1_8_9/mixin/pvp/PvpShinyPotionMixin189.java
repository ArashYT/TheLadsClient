package com.thelads.core.v1_8_9.mixin.pvp;

import com.thelads.core.client.PvpVisuals;
import com.thelads.core.modules.GlintColorizerModule;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Glint Colorizer's Shiny Potions: 1.8.9 potions with effects glint already; with the option off they stop (drawing only). */
@Mixin(ItemPotion.class)
public class PvpShinyPotionMixin189 {
    @Inject(method = "hasEffect", at = @At("HEAD"), cancellable = true)
    private void lads$shiny(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        GlintColorizerModule glint = PvpVisuals.GLINT;
        if (glint.on && !glint.shiny) cir.setReturnValue(false);
    }
}
