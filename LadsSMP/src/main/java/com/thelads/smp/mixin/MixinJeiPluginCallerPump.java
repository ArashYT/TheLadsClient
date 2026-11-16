package com.thelads.smp.mixin;

import com.thelads.smp.client.LoadingPump;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** JEI calls its ~25 plugins one after the other for every stage: a pump point before each call. */
@Pseudo
@Mixin(targets = "mezz.jei.library.load.PluginCaller", remap = false)
public class MixinJeiPluginCallerPump {
    @Inject(method = "callOnPlugins", at = @At("HEAD"), remap = false, require = 0)
    private static void ladssmp$pump(CallbackInfo ci) {
        LoadingPump.poll();
    }
}
