package com.thelads.smp.mixin;

import com.thelads.smp.client.LoadingPump;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Bits and Balance's JEI kiln support walks every smelting recipe of the game inside JEI's runtime stage; pump points at its entries (the walk itself is covered by the component pump). */
@Pseudo
@Mixin(targets = "org.onenonly.bitsandbalance.common.compat.jei.JeiKilnSupport", remap = false)
public class MixinBitsAndBalanceJeiKilnPump {
    @Inject(method = "registerRuntimeRecipes", at = @At("HEAD"), remap = false, require = 0)
    private static void ladssmp$pumpRegister(CallbackInfo ci) {
        LoadingPump.poll();
    }

    @Inject(method = "discoverModdedSmeltingRecipes", at = @At("HEAD"), remap = false, require = 0)
    private static void ladssmp$pumpDiscover(CallbackInfoReturnable<java.util.List<?>> cir) {
        LoadingPump.poll();
    }
}
