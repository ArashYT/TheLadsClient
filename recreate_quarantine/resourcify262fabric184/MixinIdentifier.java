package com.thelads.core.mixin.auto.resourcify262fabric184;

import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Identifier.class)
public class MixinIdentifier {

    @Inject(method = "toString", at = @At("HEAD"), require = 0)
    private void onToString(CallbackInfo ci) {
        // Minimal safe injection point
    }
}
