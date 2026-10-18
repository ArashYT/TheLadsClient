package com.thelads.core.v1_20_1.game.misc.mixin;

import com.thelads.core.v1_20_1.game.misc.NativeDynamicLights1201;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hooks clientbound explosion packets to spawn dynamic explosion light flashes. */
@Mixin(ClientPacketListener.class)
abstract class DynamicLightsExplosionMixin {
    @Inject(method = "handleExplosion(Lnet/minecraft/network/protocol/game/ClientboundExplodePacket;)V", at = @At("TAIL"))
    private void lads$onExplosion(ClientboundExplodePacket packet, CallbackInfo ci) {
        NativeDynamicLights1201.onExplosion(packet.getX(), packet.getY(), packet.getZ(), packet.getPower());
    }
}
