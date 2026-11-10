package com.thelads.core.v26_2.mixin;

import com.thelads.core.client.ladsapi.LadsApiClient;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.DisconnectionDetails;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ensures LadsApiClient resets any server policy and presence upon disconnection.
 */
@Mixin(ClientCommonPacketListenerImpl.class)
abstract class LadsApiDisconnectMixin {
    @Inject(method = "onDisconnect", at = @At("HEAD"), require = 1)
    private void lads$onDisconnect(DisconnectionDetails details, CallbackInfo ci) {
        LadsApiClient.reset();
    }
}
