package com.thelads.core.v26_2.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.client.ChatHistory;
import com.thelads.core.perf.ChatAsync;
import com.thelads.core.v26_2.feature.ChatAsync26;
import com.thelads.core.v26_2.feature.NativeChatHeads;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Async Chat (ChatAsync26): the layout of a new message (the line wrapping) is queued and applied in arrival order; the message
 * itself is stored at once, so rename, compact, filters and heads still run synchronously. A clear, a delete or a rescale
 * relays out everything from the stored messages, so those drop whatever is still queued. Stands down in infinite-history mode
 * (its lazy layout keeps a counter that needs every add in order, synchronously).
 */
@Mixin(ChatComponent.class)
public abstract class AsyncChatMixin26 {
    @Shadow private int getWidth() { throw new AssertionError(); }
    @Shadow private double getScale() { throw new AssertionError(); }
    @Shadow private void addMessageToDisplayQueue(GuiMessage message) { throw new AssertionError(); }

    @WrapOperation(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;addMessageToDisplayQueue(Lnet/minecraft/client/multiplayer/chat/GuiMessage;)V"), require = 1)
    private void lads$asyncLayout(ChatComponent chat, GuiMessage message, Operation<Void> original) {
        if (!ChatAsync.on() || ChatAsync.applying || ChatHistory.infinite()) {
            original.call(chat, message);
            return;
        }
        int width = Mth.floor((double) getWidth() / getScale()) - NativeChatHeads.offset(message);
        if (message.tag() != null && message.tag().icon() != null) width -= message.tag().icon().width + 4 + 2;
        ChatAsync26.add(message.content(), width, () -> addMessageToDisplayQueue(message));
    }

    @Inject(method = "refreshTrimmedMessages", at = @At("HEAD"), require = 1)
    private void lads$asyncRefresh(CallbackInfo ci) {
        ChatAsync.cancelPending();
    }

    @Inject(method = "clearMessages", at = @At("HEAD"), require = 1)
    private void lads$asyncClear(boolean history, CallbackInfo ci) {
        ChatAsync.cancelPending();
    }
}
