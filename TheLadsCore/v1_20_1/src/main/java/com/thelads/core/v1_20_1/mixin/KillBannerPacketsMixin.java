package com.thelads.core.v1_20_1.mixin;

import com.thelads.core.v1_20_1.feature.NativeKillBanner1201;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class KillBannerPacketsMixin {
    @Inject(method = "handleDamageEvent", at = @At("TAIL"), require = 1)
    private void lads$damage(ClientboundDamageEventPacket packet, CallbackInfo callback) {
        NativeKillBanner1201.damaged(packet.entityId(), packet.sourceCauseId());
    }

    @Inject(method = "handleEntityEvent", at = @At("TAIL"), require = 1)
    private void lads$death(ClientboundEntityEventPacket packet, CallbackInfo callback) {
        if (packet.getEventId() == EntityEvent.DEATH && Minecraft.getInstance().level != null) {
            var ent = packet.getEntity(Minecraft.getInstance().level);
            NativeKillBanner1201.died(ent);
            if (ent != null && ent == Minecraft.getInstance().player) {
                com.thelads.core.client.DeathTracker.get().onDeath(ent.getX(), ent.getY(), ent.getZ(),
                    Minecraft.getInstance().level.dimension().location().toString(), "Player died");
            }
        }
    }

    @Inject(method = "handleSetEntityData", at = @At("TAIL"), require = 1)
    private void lads$health(ClientboundSetEntityDataPacket packet, CallbackInfo callback) {
        if (Minecraft.getInstance().level != null && Minecraft.getInstance().level.getEntity(packet.id()) instanceof LivingEntity living
            && living.getHealth() <= 0) {
            NativeKillBanner1201.died(living);
        }
    }

    /** Combo Sounds: your arrow hit a player (the server's arrow-hit event). */
    @Inject(method = "handleGameEvent", at = @At("TAIL"), require = 1)
    private void lads$arrowHitPlayer(ClientboundGameEventPacket packet, CallbackInfo callback) {
        if (packet.getEvent() == ClientboundGameEventPacket.ARROW_HIT_PLAYER) NativeKillBanner1201.arrowHitPlayer();
    }

    @Inject(method = "handleSystemChat", at = @At("TAIL"), require = 1)
    private void lads$chat(ClientboundSystemChatPacket packet, CallbackInfo callback) {
        if (!packet.overlay()) {
            NativeKillBanner1201.chat(packet.content());
        }
    }
}
