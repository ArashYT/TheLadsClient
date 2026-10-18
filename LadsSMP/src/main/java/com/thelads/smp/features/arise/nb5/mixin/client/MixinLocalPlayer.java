package com.thelads.smp.features.arise.nb5.mixin.client;

import com.thelads.smp.features.arise.nb5.client.ParkourClientController;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into LocalPlayer to poll input keys, detect obstacles, trigger parkour movements,
 * and dispatch action payloads.
 */
@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayer {

    @Inject(
            method = "aiStep",
            at = @At("TAIL")
    )
    private void ladssmp$onAiStepParkour(CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        ParkourClientController.onLocalPlayerAiStep(self);
    }
}
