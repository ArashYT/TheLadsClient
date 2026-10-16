package com.thelads.core.v26_2.mixin;

import net.minecraft.server.packs.repository.PackCompatibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * With "No pack compat check" on (the default), treats all resource pack format versions as compatible so PackRepository startup validation,
 * Options.loadSelectedResourcePacks, and the resource pack selection screen never drop or warn on older/newer packs.
 */
@Mixin(PackCompatibility.class)
public abstract class NoResourcePackWarningsMixin {
    @Inject(method = "isCompatible", at = @At("HEAD"), cancellable = true)
    private void lads$alwaysCompatible(CallbackInfoReturnable<Boolean> cir) {
        if (com.thelads.core.client.pack.PackMenuSettings.noCompatCheck()) cir.setReturnValue(true);
    }
}
