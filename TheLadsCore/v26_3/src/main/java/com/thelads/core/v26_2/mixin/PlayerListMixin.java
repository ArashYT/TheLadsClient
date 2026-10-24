package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.social.SocialBridge26;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.SocketAddress;
import java.util.UUID;
import java.util.function.Predicate;

@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
    @Inject(
        method = "canPlayerLogin(Ljava/net/SocketAddress;Lnet/minecraft/server/players/NameAndId;)Lnet/minecraft/network/chat/Component;",
        at = @At("HEAD"),
        cancellable = true,
        require = 1
    )
    private void ladsCheckLoginFilter(SocketAddress socketAddress, NameAndId nameAndId, CallbackInfoReturnable<Component> cir) {
        Predicate<UUID> filter = SocialBridge26.getLoginFilter();
        if (filter != null) {
            if (nameAndId == null || nameAndId.id() == null || !filter.test(nameAndId.id())) {
                cir.setReturnValue(Component.literal("You weren't invited to this world"));
            }
        }
    }

    @Inject(
        method = "broadcastChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/chat/ChatType$Bound;)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void ladsFilterMutedGuestChat(PlayerChatMessage message, ServerPlayer sender, ChatType.Bound bound, CallbackInfo ci) {
        if (sender != null && SocialBridge26.isMuted(sender.getUUID())) {
            sender.sendSystemMessage(Component.literal("§cYou are muted by the host."));
            ci.cancel();
        }
    }
}
