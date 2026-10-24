package com.thelads.core.v26_2.hudextras.mixin;

import com.thelads.core.client.hud.HudExtras;
import com.thelads.core.v26_2.hudextras.HudExtras26;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Totem Pop Counter: the entity events every client gets (35 a totem saved someone, 3 a death), read on the game thread after vanilla
 * handled them (the network thread's call stops at ensureRunningOnSameThread). Nothing is sent.
 */
@Mixin(ClientPacketListener.class)
public class TotemMixin26 {
    @Shadow private ClientLevel level;

    @Inject(method = "handleEntityEvent(Lnet/minecraft/network/protocol/game/ClientboundEntityEventPacket;)V", at = @At("TAIL"))
    private void lads$totems(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        if (!HudExtras.totems || level == null) return;
        Entity entity = packet.getEntity(level);
        if (entity != null) HudExtras26.entityEvent(entity, packet.getEventId());
    }
}
