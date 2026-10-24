package com.thelads.core.v1_20_1.mixin.social;

import com.thelads.core.v1_20_1.social.SocialBridge1201;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to enforce host-muted guest status on the integrated server.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerChatMuteMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "handleChat(Lnet/minecraft/network/protocol/game/ServerboundChatPacket;)V", at = @At("HEAD"), cancellable = true)
    private void ladsCheckMuted(ServerboundChatPacket packet, CallbackInfo ci) {
        if (player != null && SocialBridge1201.isMuted(player.getUUID())) {
            player.sendSystemMessage(Component.literal("You are muted in this world."));
            ci.cancel();
        }
    }
}
