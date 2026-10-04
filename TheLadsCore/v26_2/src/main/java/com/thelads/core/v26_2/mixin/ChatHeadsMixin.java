package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.NativeChatHeads;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Chat Heads (NativeChatHeads): each new message's sender, and the graphics its heads are drawn into. */
@Mixin(ChatComponent.class)
public abstract class ChatHeadsMixin {
    @Unique private static final String DRAW = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V";

    @Shadow public abstract void rescaleChat();

    @ModifyArg(method = "addMessage", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;addMessageToDisplayQueue(Lnet/minecraft/client/multiplayer/chat/GuiMessage;)V"), require = 1)
    private GuiMessage lads$sender(GuiMessage message) {
        return NativeChatHeads.attach(message);
    }

    @Inject(method = DRAW, at = @At("HEAD"), require = 1)
    private void lads$headsDraw(GuiGraphicsExtractor graphics, Font font, int ticks, int mouseX, int mouseY, ChatComponent.DisplayMode mode, boolean cursor, CallbackInfo ci) {
        if (NativeChatHeads.drawing(graphics)) rescaleChat();
    }

    @Inject(method = DRAW, at = @At("RETURN"), require = 1)
    private void lads$headsDrawn(CallbackInfo ci) {
        NativeChatHeads.drawing(null);
    }
}
