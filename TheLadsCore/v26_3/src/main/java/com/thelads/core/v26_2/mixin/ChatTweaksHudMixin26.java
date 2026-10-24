package com.thelads.core.v26_2.mixin;

import com.thelads.core.client.ChatTweaks;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Chat Peek (ChatTweaks26): while its key is held the HUD draws the chat as the chat screen does (every line, no fade). */
@Mixin(Hud.class)
public abstract class ChatTweaksHudMixin26 {
    @ModifyArg(method = "extractChat", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V"), index = 5, require = 1)
    private ChatComponent.DisplayMode lads$peek(ChatComponent.DisplayMode mode) {
        return ChatTweaks.peeking ? ChatComponent.DisplayMode.FOREGROUND : mode;
    }
}
