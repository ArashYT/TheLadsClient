package com.thelads.core.v26_2.mixin;

import com.thelads.core.client.ChatPeek;
import com.thelads.core.client.ChatSearch;
import com.thelads.core.client.ChatShortcuts;
import com.thelads.core.client.ChatTabs;
import com.thelads.core.modules.UnlimitedCommandLengthModule;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
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
    @Shadow public abstract void handleChatInput(String message, boolean addToHistory);

    private ChatScreenFeaturesMixin(Component title) { super(title); }

    @Inject(method = "init", at = @At("TAIL"))
    private void lads$initChatFeatures(CallbackInfo ci) {
        if (input != null) {
            input.setMaxLength(UnlimitedCommandLengthModule.getCommandMaxLength(256));
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void lads$chatKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        // Ctrl+F toggles ChatSearch
        if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_F && (event.modifiers() & com.mojang.blaze3d.platform.InputConstants.MOD_CONTROL) != 0) {
            ChatSearch.toggle();
            if (minecraft != null) minecraft.gui.hud.getChat().rescaleChat();
            cir.setReturnValue(true);
            return;
        }

        // Enter or Numpad Enter with Shift
        if ((event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN || event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER)
                && (event.modifiers() & com.mojang.blaze3d.platform.InputConstants.MOD_SHIFT) != 0 && ChatPeek.keepOpenEnabled()) {
            if (input != null) {
                String text = ChatShortcuts.expand(input.getValue());
                if (minecraft != null && minecraft.player != null && !text.trim().isEmpty()) {
                    handleChatInput(text, true);
                }
                input.setValue("");
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void lads$chatMouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (event.button() == 0 && ChatTabs.enabled() && minecraft != null) {
            int tabCount = ChatTabs.getTabCount();
            int x = 2;
            int y = height - 14 - (int) com.thelads.core.v26_2.feature.NativeQualityOfLife.number("Chat", "Chat Height", 180);
            for (int i = 0; i < tabCount; i++) {
                String name = ChatTabs.getTab(i).getName();
                int width = minecraft.font.width(name) + 6;
                if (event.x() >= x && event.x() <= x + width && event.y() >= y && event.y() <= y + minecraft.font.lineHeight + 4) {
                    ChatTabs.setActiveTab(i);
                    minecraft.gui.hud.getChat().rescaleChat();
                    cir.setReturnValue(true);
                    return;
                }
                x += width + 4;
            }
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void lads$drawChatTabs(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!ChatTabs.enabled() || minecraft == null) return;
        int tabCount = ChatTabs.getTabCount();
        int active = ChatTabs.getActiveTab();
        int x = 2;
        int y = height - 14 - (int) com.thelads.core.v26_2.feature.NativeQualityOfLife.number("Chat", "Chat Height", 180);
        for (int i = 0; i < tabCount; i++) {
            String name = ChatTabs.getTab(i).getName();
            int width = minecraft.font.width(name) + 6;
            int bg = (i == active) ? 0x90000000 : 0x50000000;
            graphics.fill(x, y - 2, x + width, y + minecraft.font.lineHeight + 2, bg);
            graphics.text(minecraft.font, name, x + 3, y, (i == active) ? 0xFFFF55 : 0xAAAAAA, false);
            x += width + 4;
        }
    }
}
