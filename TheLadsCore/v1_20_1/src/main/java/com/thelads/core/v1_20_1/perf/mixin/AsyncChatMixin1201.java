package com.thelads.core.v1_20_1.perf.mixin;

import com.thelads.core.perf.ChatAsync;
import com.thelads.core.v1_20_1.perf.ChatAsync1201;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Async Chat (ChatAsync1201): incoming messages go through one ordered queue; deletes wait behind queued adds; a clear drops
 * queued adds; a rescale makes queued wraps stale. With the option off (or -Dlads.asyncChat=false) every hook returns at once.
 * Low priority number: runs before the other ChatComponent mixins, so a queued message does not start their animation twice.
 */
@Mixin(value = ChatComponent.class, priority = 500)
public abstract class AsyncChatMixin1201 {
    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
        at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$asyncAdd(Component content, MessageSignature signature, GuiMessageTag tag, CallbackInfo ci) {
        if (!ChatAsync.on() || ChatAsync.applying) return;
        ci.cancel();
        ChatAsync1201.add((ChatComponent) (Object) this, content, signature, tag);
    }

    @Inject(method = "deleteMessage", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$asyncDelete(MessageSignature signature, CallbackInfo ci) {
        if (!ChatAsync.on() || ChatAsync.applying || !ChatAsync1201.pending()) return;
        ci.cancel();
        ChatAsync1201.delete((ChatComponent) (Object) this, signature);
    }

    @Inject(method = "clearMessages", at = @At("HEAD"), require = 1)
    private void lads$asyncClear(boolean history, CallbackInfo ci) {
        ChatAsync.cancelPending();
    }

    @Inject(method = "rescaleChat", at = @At("HEAD"), require = 1)
    private void lads$asyncRescale(CallbackInfo ci) {
        ChatAsync.invalidate();
    }
}
