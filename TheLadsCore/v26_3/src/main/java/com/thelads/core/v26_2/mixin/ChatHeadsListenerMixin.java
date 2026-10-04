package com.thelads.core.v26_2.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.authlib.GameProfile;
import com.thelads.core.v26_2.feature.NativeChatHeads;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Chat Heads (NativeChatHeads): signed player chat is added with the sender the server named, so its head needs no guess. */
@Mixin(ChatListener.class)
public abstract class ChatHeadsListenerMixin {
    @WrapOperation(method = "showMessageToPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;addPlayerMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V"), require = 1)
    private void lads$signedSender(ChatComponent chat, Component message, MessageSignature signature, GuiMessageTag tag, Operation<Void> original,
                                   @Local(argsOnly = true) GameProfile sender) {
        NativeChatHeads.signedSender = sender;
        try {
            original.call(chat, message, signature, tag);
        } finally {
            NativeChatHeads.signedSender = null;
        }
    }
}
