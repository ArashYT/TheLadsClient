package com.thelads.smp.mixin;

import com.thelads.smp.config.LadsConfig;
import com.thelads.smp.features.LoginProtectionManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Consolidates 'BetterJoinMessages' & 'logprot':
 * - Suppresses vanilla leave message in ServerGamePacketListenerImpl.removePlayerFromWorld.
 * - Clears temporary login invulnerability once player rotates or moves.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MixinServerGamePacketListenerImpl {

    @Shadow
    public ServerPlayer player;

    @Redirect(
            method = "removePlayerFromWorld()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V"
            )
    )
    private void ladssmp$suppressVanillaLeaveMessage(PlayerList instance, Component message, boolean bypassHiddenChat) {
        if (!LadsConfig.get().features.betterJoinMessages) {
            instance.broadcastSystemMessage(message, bypassHiddenChat);
        }
    }

    @Inject(method = "handleMovePlayer", at = @At("HEAD"))
    private void ladssmp$clearLoginProtectionOnMove(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        if (LoginProtectionManager.hasProtection(this.player)) {
            boolean rotated = packet.hasRotation() && (
                    Math.abs(packet.getXRot(this.player.getXRot()) - this.player.getXRot()) > 0.1f ||
                    Math.abs(packet.getYRot(this.player.getYRot()) - this.player.getYRot()) > 0.1f
            );
            boolean moved = packet.hasPosition() && (
                    Math.abs(packet.getX(this.player.getX()) - this.player.getX()) > 0.1 ||
                    Math.abs(packet.getY(this.player.getY()) - this.player.getY()) > 0.1 ||
                    Math.abs(packet.getZ(this.player.getZ()) - this.player.getZ()) > 0.1
            );
            if (rotated || moved) {
                LoginProtectionManager.removeProtection(this.player);
            }
        }
    }
}
