package com.thelads.core.v1_21_11.ladsapi.ui.mixin;

import com.thelads.core.v1_21_11.ladsapi.ui.ServerKeys12111;
import net.minecraft.client.resources.language.ClientLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The Controls screen shows a server action by the name the server gave it. */
@Mixin(ClientLanguage.class)
public abstract class ServerKeyLangMixin12111 {
    @Inject(method = "getOrDefault(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", at = @At("HEAD"), cancellable = true)
    private void lads$name(String key, String fallback, CallbackInfoReturnable<String> cir) {
        String name = ServerKeys12111.label(key);
        if (name != null) cir.setReturnValue(name);
    }

    @Inject(method = "has(Ljava/lang/String;)Z", at = @At("HEAD"), cancellable = true)
    private void lads$has(String key, CallbackInfoReturnable<Boolean> cir) {
        if (ServerKeys12111.label(key) != null) cir.setReturnValue(true);
    }
}
