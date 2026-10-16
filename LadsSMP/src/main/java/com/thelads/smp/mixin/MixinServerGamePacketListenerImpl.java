package com.thelads.smp.mixin;

import com.thelads.smp.config.LadsConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Consolidates 'BetterJoinMessages': Suppresses vanilla leave message in ServerGamePacketListenerImpl.removePlayerFromWorld.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MixinServerGamePacketListenerImpl {

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
}
