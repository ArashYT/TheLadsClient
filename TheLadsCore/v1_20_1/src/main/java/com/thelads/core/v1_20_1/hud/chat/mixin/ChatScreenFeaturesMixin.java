package com.thelads.core.v1_20_1.hud.chat.mixin;

import com.thelads.core.client.ChatPeek;
import com.thelads.core.client.ChatSearch;
import com.thelads.core.client.ChatShortcuts;
import com.thelads.core.client.ChatTabs;
import com.thelads.core.modules.UnlimitedCommandLengthModule;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatScreenFeaturesMixin extends Screen {
    @Shadow protected EditBox input;

    private ChatScreenFeaturesMixin(Component title) { super(title); }

    @Inject(method = "init", at = @At("TAIL"))
    private void lads$initChatFeatures(CallbackInfo ci) {
        if (input != null) {
            input.setMaxLength(UnlimitedCommandLengthModule.getCommandMaxLength(256));
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void lads$chatKeyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        // Ctrl+F toggles ChatSearch
        if (keyCode == 70 && hasControlDown()) { // 70 = KEY_F
            ChatSearch.toggle();
            if (minecraft != null) minecraft.gui.getChat().rescaleChat();
            cir.setReturnValue(true);
            return;
        }

        // Enter or Numpad Enter with Shift
        if ((keyCode == 257 || keyCode == 335) && hasShiftDown() && ChatPeek.keepOpenEnabled()) {
            if (input != null) {
                String text = ChatShortcuts.expand(input.getValue());
                if (minecraft != null && minecraft.player != null && !text.trim().isEmpty()) {
                    if (text.startsWith("/")) minecraft.player.connection.sendCommand(text.substring(1));
                    else minecraft.player.connection.sendChat(text);
                }
                input.setValue("");
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void lads$chatMouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (button == 0 && ChatTabs.enabled() && minecraft != null) {
            int tabCount = ChatTabs.getTabCount();
            int x = 2;
            int y = height - 14 - (int) com.thelads.core.v1_20_1.hud.Hud1201.number("Chat", "Chat Height", 180);
            for (int i = 0; i < tabCount; i++) {
                String name = ChatTabs.getTab(i).getName();
                int width = minecraft.font.width(name) + 6;
                if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + minecraft.font.lineHeight + 4) {
                    ChatTabs.setActiveTab(i);
                    minecraft.gui.getChat().rescaleChat();
                    cir.setReturnValue(true);
                    return;
                }
                x += width + 4;
            }
        }
    }
}
