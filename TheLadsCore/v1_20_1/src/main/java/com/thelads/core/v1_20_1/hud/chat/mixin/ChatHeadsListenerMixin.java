package com.thelads.core.v1_20_1.hud.chat.mixin;

import com.mojang.authlib.GameProfile;
import com.thelads.core.v1_20_1.hud.chat.NativeChatHeads1201;
import java.time.Instant;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Chat Heads (NativeChatHeads1201): signed player chat is added with the sender the server named, so its head needs no guess. */
@Mixin(ChatListener.class)
public abstract class ChatHeadsListenerMixin {
    @Inject(method = "showMessageToPlayer", at = @At("HEAD"), require = 1)
    private void lads$signedSender(ChatType.Bound bound, PlayerChatMessage message, Component content, GameProfile sender, boolean onlySecure, Instant received,
                                   CallbackInfoReturnable<Boolean> cir) {
        NativeChatHeads1201.signedSender = sender;
    }

    @Inject(method = "showMessageToPlayer", at = @At("RETURN"), require = 1)
    private void lads$signedSenderDone(CallbackInfoReturnable<Boolean> cir) {
        NativeChatHeads1201.signedSender = null;
    }
}
