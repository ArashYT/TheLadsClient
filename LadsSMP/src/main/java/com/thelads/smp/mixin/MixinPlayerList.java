package com.thelads.smp.mixin;

import com.thelads.smp.config.LadsConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Consolidates 'BetterJoinMessages': Suppresses vanilla join message in PlayerList.placeNewPlayer when custom join messages are active.
 */
@Mixin(PlayerList.class)
public abstract class MixinPlayerList {

    @Redirect(
            method = "placeNewPlayer(Lnet/minecraft/network/Connection;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/server/network/CommonListenerCookie;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V"
            )
    )
    private void ladssmp$suppressVanillaJoinMessage(PlayerList instance, Component message, boolean bypassHiddenChat) {
        if (!LadsConfig.get().features.betterJoinMessages) {
            instance.broadcastSystemMessage(message, bypassHiddenChat);
        }
    }

    @org.spongepowered.asm.mixin.injection.Inject(method = "remove", at = @At("HEAD"))
    private void ladssmp$onPlayerRemove(net.minecraft.server.level.ServerPlayer player, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        com.thelads.smp.features.LimitedChunksManager.onPlayerLeave(player);
    }
}
