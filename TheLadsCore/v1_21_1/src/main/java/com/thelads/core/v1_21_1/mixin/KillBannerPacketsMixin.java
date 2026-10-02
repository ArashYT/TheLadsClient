package com.thelads.core.v1_21_1.mixin;

import com.thelads.core.v1_21_1.feature.NativeKillBanner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** KillBanner: what the server reports about hits, deaths and kills, as each packet is applied on the client thread. */
@Mixin(ClientPacketListener.class)
public class KillBannerPacketsMixin {
    @Inject(method = "handleDamageEvent", at = @At("TAIL"), require = 1)
    private void lads$damage(ClientboundDamageEventPacket packet, CallbackInfo callback) {
        NativeKillBanner.damaged(packet.entityId(), packet.sourceCauseId());
    }

    @Inject(method = "handleEntityEvent", at = @At("TAIL"), require = 1)
    private void lads$death(ClientboundEntityEventPacket packet, CallbackInfo callback) {
        if (packet.getEventId() == EntityEvent.DEATH && Minecraft.getInstance().level != null)
            NativeKillBanner.died(packet.getEntity(Minecraft.getInstance().level));
    }

    @Inject(method = "handleSetEntityData", at = @At("TAIL"), require = 1)
    private void lads$health(ClientboundSetEntityDataPacket packet, CallbackInfo callback) {
        if (Minecraft.getInstance().level != null && Minecraft.getInstance().level.getEntity(packet.id()) instanceof LivingEntity living
            && living.getHealth() <= 0) NativeKillBanner.died(living);
    }

    @Inject(method = "handleSystemChat", at = @At("TAIL"), require = 1)
    private void lads$chat(ClientboundSystemChatPacket packet, CallbackInfo callback) {
        if (!packet.overlay()) NativeKillBanner.chat(packet.content());
    }
}
