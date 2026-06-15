package com.thelads.core.mixin;

import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Replaces the external chat-signing-hider mod: strips the signing/"modified"
 * indicator tags (colored bars + tooltips) from incoming chat messages.
 */
@Mixin(ChatComponent.class)
public class ChatComponentMixin {

    @ModifyVariable(
        method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
        at = @At("HEAD"), argsOnly = true, require = 0
    )
    private GuiMessageTag ladsStripIndicator(GuiMessageTag tag) {
        Module m = ModuleManager.getInstance().getModule("HideChatIndicators");
        return (m != null && m.isEnabled()) ? null : tag;
    }
}
