package com.thelads.core.v1_20_1.mixin;

import net.minecraft.server.packs.repository.PackCompatibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Treats all resource pack format versions as compatible so PackRepository startup validation,
 * Options.loadSelectedResourcePacks, and the resource pack selection screen never drop or warn on older/newer packs.
 */
@Mixin(PackCompatibility.class)
public abstract class NoResourcePackWarningsMixin {
    @Inject(method = "isCompatible", at = @At("HEAD"), cancellable = true)
    private void lads$alwaysCompatible(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(true);
    }
}
