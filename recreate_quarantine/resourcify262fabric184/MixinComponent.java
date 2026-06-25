package com.thelads.core.mixin.auto.resourcify262fabric184;

import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Component.class)
public class MixinComponent {

    @Inject(method = "getString", at = @At("HEAD"), require = 0)
    private void onGetString(CallbackInfo ci) {
        // Minimal safe injection point
    }
}
