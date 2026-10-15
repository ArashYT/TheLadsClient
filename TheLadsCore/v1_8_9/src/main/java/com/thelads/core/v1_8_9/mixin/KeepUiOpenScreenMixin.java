package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.client.KeepUiOpen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiDownloadTerrain;
import net.minecraft.client.gui.GuiGameOver;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSleepMP;
import net.minecraft.client.gui.GuiTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Keep UI Open: a chat pushed away by the death or terrain-loading screen comes back, with its text, when that screen closes. */
@Mixin(Minecraft.class)
public abstract class KeepUiOpenScreenMixin {
    @ModifyVariable(method = "displayGuiScreen", at = @At("HEAD"), argsOnly = true)
    private GuiScreen lads$keepChat(GuiScreen next) {
        GuiScreen current = ((Minecraft) (Object) this).currentScreen;
        GuiTextField input = current instanceof GuiChat ? ((GuiChatInputAccessor) current).lads$input() : null;
        String text = KeepUiOpen.screenChange(kind(current), input == null ? null : input.getText(), kind(next));
        return text == null ? next : new GuiChat(text);
    }

    private static KeepUiOpen.Kind kind(GuiScreen screen) {
        if (screen == null) return KeepUiOpen.Kind.NONE;
        if (screen instanceof GuiChat && !(screen instanceof GuiSleepMP)) return KeepUiOpen.Kind.CHAT;
        if (screen instanceof GuiDownloadTerrain || screen instanceof GuiGameOver) return KeepUiOpen.Kind.TRANSITION;
        return KeepUiOpen.Kind.OTHER;
    }
}
