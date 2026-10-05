package com.thelads.core.v26_2.mixin;

import com.thelads.core.client.DamageTilt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Damage tilt: the local player's hurts and their directions, as each packet is applied on the client thread. The server sends a
 * hurt animation (with the hit's yaw) only for hits that knock back; fall, fire and other damage come as a damage event alone.
 */
@Mixin(ClientPacketListener.class)
public class DamageTiltPacketsMixin {
    @Inject(method = "handleDamageEvent", at = @At("TAIL"), require = 1)
    private void lads$hurt(ClientboundDamageEventPacket packet, CallbackInfo callback) {
        if (own(packet.entityId())) DamageTilt.CLIENT.hurt(System.currentTimeMillis());
    }

    @Inject(method = "handleHurtAnimation", at = @At("TAIL"), require = 1)
    private void lads$direction(ClientboundHurtAnimationPacket packet, CallbackInfo callback) {
        if (!own(packet.id())) return;
        long now = System.currentTimeMillis();
        DamageTilt.CLIENT.hurt(now); // the animation starts a hurt itself
        DamageTilt.CLIENT.direction(packet.yaw(), now);
    }

    private static boolean own(int id) {
        var player = Minecraft.getInstance().player;
        return player != null && player.getId() == id;
    }
}
