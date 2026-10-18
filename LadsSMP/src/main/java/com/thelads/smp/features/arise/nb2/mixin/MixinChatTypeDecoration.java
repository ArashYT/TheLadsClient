package com.thelads.smp.features.arise.nb2.mixin;

import com.thelads.smp.features.arise.nb2.RemoveChatBracketManager;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.ChatTypeDecoration;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to ChatTypeDecoration to strip angle brackets from standard player messages (Remove Chat Bracket).
 */
@Mixin(ChatTypeDecoration.class)
public abstract class MixinChatTypeDecoration {

    @Shadow
    public abstract String translationKey();

    @Shadow
    public abstract Style style();

    @Inject(
            method = "decorate(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/ChatType$Bound;)Lnet/minecraft/network/chat/Component;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onDecorate(Component content, ChatType.Bound bound, CallbackInfoReturnable<Component> cir) {
        if ("chat.type.text".equals(this.translationKey()) && RemoveChatBracketManager.isRemoveChatBracketEnabled()) {
            Component formatted = RemoveChatBracketManager.formatMessage(bound.name(), content, this.style());
            if (formatted != null) {
                cir.setReturnValue(formatted);
            }
        }
    }
}
