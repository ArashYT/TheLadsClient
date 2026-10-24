package com.thelads.core.v1_20_1.game.mixin;

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
    @Inject(method = "handleDamageEvent", at = @At("TAIL"))
    private void lads$hurt(ClientboundDamageEventPacket packet, CallbackInfo callback) {
        if (!own(packet.entityId())) return;
        long now = System.currentTimeMillis();
        DamageTilt.CLIENT.hurt(now);
        DamageTilt.CLIENT.direction(Float.NaN, now);
    }

    @Inject(method = "handleHurtAnimation", at = @At("TAIL"))
    private void lads$direction(ClientboundHurtAnimationPacket packet, CallbackInfo callback) {
        if (!own(packet.id())) return;
        long now = System.currentTimeMillis();
        DamageTilt.CLIENT.hurt(now);
        DamageTilt.CLIENT.direction(packet.yaw(), now);
    }

    @Inject(method = "handleSetTime", at = @At("TAIL"))
    private void lads$time(net.minecraft.network.protocol.game.ClientboundSetTimePacket packet, CallbackInfo callback) {
        com.thelads.core.client.TpsTracker.get().onTimeUpdate(packet.getGameTime());
    }

    private static boolean own(int id) {
        var player = Minecraft.getInstance().player;
        return player != null && player.getId() == id;
    }
}
