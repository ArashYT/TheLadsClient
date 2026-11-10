package com.thelads.core.v1_20_1.mixin;

import com.thelads.core.client.ladsapi.ServerSettings;
import com.thelads.core.v1_20_1.net.NativeServerOptions;
import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A LadsClientAPI server's rule on a vanilla option: the getter gives the effective value, the stored one stays the player's own. */
@Mixin(OptionInstance.class)
public abstract class ServerOptionMixin {
    @Inject(method = "get", at = @At("RETURN"), cancellable = true)
    private void lads$serverRule(CallbackInfoReturnable<Object> cir) {
        if (!ServerSettings.hasVanillaRules()) return;
        Object effective = NativeServerOptions.effective(this, cir.getReturnValue());
        if (effective != null) cir.setReturnValue(effective);
    }
}