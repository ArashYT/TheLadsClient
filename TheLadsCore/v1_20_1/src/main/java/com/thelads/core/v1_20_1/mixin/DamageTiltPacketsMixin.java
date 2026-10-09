package com.thelads.core.v1_20_1.mixin;

import com.thelads.core.client.DamageTilt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class DamageTiltPacketsMixin {
    @Inject(method = "handleDamageEvent", at = @At("TAIL"), require = 0)
    private void lads$hurt(ClientboundDamageEventPacket packet, CallbackInfo callback) {
        if (!own(packet.entityId())) return;
        long now = System.currentTimeMillis();
        DamageTilt.CLIENT.hurt(now);
        DamageTilt.CLIENT.direction(Float.NaN, now);
    }

    @Inject(method = "handleHurtAnimation", at = @At("TAIL"), require = 0)
    private void lads$direction(ClientboundHurtAnimationPacket packet, CallbackInfo callback) {
        if (!own(packet.id())) return;
        long now = System.currentTimeMillis();
        DamageTilt.CLIENT.hurt(now);
        DamageTilt.CLIENT.direction(packet.yaw(), now);
    }

    private static boolean own(int id) {
        var player = Minecraft.getInstance().player;
        return player != null && player.getId() == id;
    }
}
