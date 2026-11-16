package com.thelads.smp.mixin;

import com.thelads.smp.client.LoadingPump;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** JEI starts on the render thread inside the login packet and takes 4-20 s; it logs every stage through LoggedTimer, so each stage boundary is a pump point. */
@Pseudo
@Mixin(targets = "mezz.jei.common.util.LoggedTimer", remap = false)
public class MixinJeiLoggedTimerPump {
    @Inject(method = {"start", "stop"}, at = @At("HEAD"), remap = false, require = 0)
    private void ladssmp$pump(CallbackInfo ci) {
        LoadingPump.poll();
    }
}
