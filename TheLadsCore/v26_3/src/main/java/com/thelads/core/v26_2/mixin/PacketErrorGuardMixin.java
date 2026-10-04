package com.thelads.core.v26_2.mixin;

import com.thelads.core.client.PacketErrorPolicy;
import com.thelads.core.v26_2.feature.ConnectionTweaks;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** IgnorePacketErrors: a play packet whose handler threw is logged and skipped instead of "Network Protocol Error". */
@Mixin(ClientCommonPacketListenerImpl.class)
abstract class PacketErrorGuardMixin {
    @Inject(method = "onPacketError", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$keepPlaying(Packet<?> packet, Exception failure, CallbackInfo ci) {
        if ((Object) this instanceof ClientPacketListener && ConnectionTweaks.ignorePacketErrors() && PacketErrorPolicy.skippable(failure)) {
            PacketErrorPolicy.skipped("play packet " + packet.type() + ", whose handler failed,", failure);
            ci.cancel();
        }
    }
}
