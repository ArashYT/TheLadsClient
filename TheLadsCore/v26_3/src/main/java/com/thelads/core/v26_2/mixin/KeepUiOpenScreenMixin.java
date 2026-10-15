package com.thelads.core.v26_2.mixin;

import com.thelads.core.client.KeepUiOpen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.InBedChatScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Keep UI Open: a chat pushed away by the death or a level-loading screen comes back, with its text, when that screen closes. */
@Mixin(Gui.class)
public abstract class KeepUiOpenScreenMixin {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true, require = 1)
    private Screen lads$keepChat(Screen next) {
        Screen current = Minecraft.getInstance().gui.screen();
        EditBox input = current instanceof ChatScreen ? ((ChatScreenInputAccessor) current).lads$input() : null;
        String text = KeepUiOpen.screenChange(kind(current), input == null ? null : input.getValue(), kind(next));
        return text == null ? next : new ChatScreen(text, false);
    }

    private static KeepUiOpen.Kind kind(Screen screen) {
        if (screen == null) return KeepUiOpen.Kind.NONE;
        if (screen instanceof ChatScreen && !(screen instanceof InBedChatScreen)) return KeepUiOpen.Kind.CHAT;
        if (screen instanceof DeathScreen || screen instanceof LevelLoadingScreen || screen instanceof ProgressScreen
            || screen instanceof GenericMessageScreen) return KeepUiOpen.Kind.TRANSITION;
        return KeepUiOpen.Kind.OTHER;
    }
}
