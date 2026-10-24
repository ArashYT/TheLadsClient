package com.thelads.core.v1_8_9.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.thelads.core.client.ChatTweaks;
import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.client.hypixel.HypixelMods;
import com.thelads.core.client.hypixel.HypixelTabFill;
import com.thelads.core.v1_8_9.feature.ChatTweaks189;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hypixel tab fill, and the chat screen's parity-1023 tweaks (ChatTweaks189): the tab row and the search box just above the input bar
 * (Ctrl+F focuses the search, Enter goes back to the message), Shift+Enter sends and keeps the chat open, Shortcuts expand the typed
 * line, and Unlimited Command Length lifts the 100-character input in singleplayer only (C01ChatLengthMixin189 keeps the packet whole).
 */
@Mixin(GuiChat.class)
public abstract class GuiChatMixin extends GuiScreen {
    @Shadow protected GuiTextField inputField;
    @Shadow private int sentHistoryCursor;
    @Unique private GuiTextField ladsSearch;

    @ModifyVariable(method = "onAutocompleteResponse([Ljava/lang/String;)V", at = @At("HEAD"), argsOnly = true)
    private String[] ladsHypixelTabComplete(String[] completions) {
        if (!HypixelSafetyManager.isHypixel() || !HypixelMods.isTabFillEnabled()) {
            return completions;
        }
        if (inputField == null) return completions;
        String text = inputField.getText();
        if (text == null || !text.startsWith("/")) return completions;

        Set<String> knownNames = HypixelTabFill.collectKnownNames();
        List<String> clientCompletions = HypixelTabFill.complete(text, knownNames);
        if (clientCompletions.isEmpty()) {
            return completions;
        }

        return HypixelTabFill.mergeCompletions(completions, clientCompletions);
    }

    @Inject(method = "initGui", at = @At("TAIL"), require = 1)
    private void ladsInit(CallbackInfo ci) {
        if (ChatTweaks.longCommands(mc.isIntegratedServerRunning())) inputField.setMaxStringLength(ChatTweaks.LONG_COMMAND_LIMIT);
        ladsSearch = null;
        if (!ChatTweaks.search) return;
        ladsSearch = new GuiTextField(0, fontRendererObj, width - 2 - ChatTweaks.SEARCH_WIDTH, height - ChatTweaks.BAR_BOTTOM - ChatTweaks.BAR_HEIGHT,
            ChatTweaks.SEARCH_WIDTH, ChatTweaks.BAR_HEIGHT);
        ladsSearch.setMaxStringLength(64);
        ladsSearch.setText(ChatTweaks.query());
    }

    @Inject(method = "onGuiClosed", at = @At("TAIL"), require = 1)
    private void ladsClosed(CallbackInfo ci) {
        if (ChatTweaks.setQuery("")) ChatTweaks189.refilter();
    }

    @Inject(method = "updateScreen", at = @At("TAIL"), require = 1)
    private void ladsCursor(CallbackInfo ci) {
        if (ladsSearch != null) ladsSearch.updateCursorCounter();
    }

    @Inject(method = "drawScreen", at = @At("TAIL"), require = 1)
    private void ladsTabs(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        int y = height - ChatTweaks.BAR_BOTTOM - ChatTweaks.BAR_HEIGHT, x = 2;
        for (int i = 0; i < ChatTweaks.tabCount(); i++) {
            String name = ChatTweaks.tabName(i);
            int w = fontRendererObj.getStringWidth(name) + 8;
            boolean active = i == ChatTweaks.activeTab(), hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + ChatTweaks.BAR_HEIGHT;
            Gui.drawRect(x, y, x + w, y + ChatTweaks.BAR_HEIGHT, active ? 0xC0000000 : hover ? 0xA0000000 : 0x60000000);
            fontRendererObj.drawString(name, x + 4, y + 2, active ? 0xFFFFFF55 : 0xFFC0C0C0);
            x += w + 2;
        }
        if (ChatTweaks.buttons) {
            ChatTweaks189.Access chat = (ChatTweaks189.Access) mc.ingameGUI.getChatGUI();
            if (mouseX >= 0 && chat.lads$hovered(height, mouseY) != null && mouseX < chat.lads$hoverRight() + 2 * ChatTweaks.BUTTON + 1) {
                int right = chat.lads$hoverRight(), top = chat.lads$hoverTop(), button = ChatTweaks.button(mouseX, right);
                for (int i = 0; i < 2; i++) {
                    int left = right + i * (ChatTweaks.BUTTON + 1);
                    Gui.drawRect(left, top, left + ChatTweaks.BUTTON, top + 9, button == i ? 0xE0303030 : 0xA0000000);
                    fontRendererObj.drawString(i == 0 ? ChatTweaks.COPY_LABEL : ChatTweaks.DELETE_LABEL, left + 3, top + 1, button == i ? 0xFFFFFF55 : 0xFFC0C0C0);
                }
            }
        }
        if (ladsSearch == null) return;
        ladsSearch.drawTextBox();
        if (!ladsSearch.isFocused() && ladsSearch.getText().isEmpty())
            fontRendererObj.drawString("Search chat", ladsSearch.xPosition + 4, ladsSearch.yPosition + 2, 0xFF606060);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsClick(int mouseX, int mouseY, int button, CallbackInfo ci) {
        if (ChatTweaks.buttons && button == 0) {
            ChatTweaks189.Access chat = (ChatTweaks189.Access) mc.ingameGUI.getChatGUI();
            net.minecraft.client.gui.ChatLine message = chat.lads$hovered(height, mouseY);
            int which = message == null ? -1 : ChatTweaks.button(mouseX, chat.lads$hoverRight());
            if (which >= 0) {
                ChatTweaks189.messageButton(message, which);
                ci.cancel();
                return;
            }
        }
        if (ladsSearch != null) {
            boolean in = mouseX >= ladsSearch.xPosition && mouseX < ladsSearch.xPosition + ladsSearch.width
                && mouseY >= ladsSearch.yPosition && mouseY < ladsSearch.yPosition + ladsSearch.height;
            ladsFocusSearch(in);
            if (in) {
                ladsSearch.mouseClicked(mouseX, mouseY, button);
                ci.cancel();
                return;
            }
        }
        int y = height - ChatTweaks.BAR_BOTTOM - ChatTweaks.BAR_HEIGHT, x = 2;
        if (button != 0 || mouseY < y || mouseY >= y + ChatTweaks.BAR_HEIGHT) return;
        for (int i = 0; i < ChatTweaks.tabCount(); i++) {
            int w = fontRendererObj.getStringWidth(ChatTweaks.tabName(i)) + 8;
            if (mouseX >= x && mouseX < x + w) {
                if (ChatTweaks.selectTab(i)) ChatTweaks189.refilter();
                ci.cancel();
                return;
            }
            x += w + 2;
        }
    }

    @Unique
    private void ladsFocusSearch(boolean search) {
        ladsSearch.setFocused(search);
        inputField.setFocused(!search);
    }

    /** Typing in the search box: every key but Escape goes to it (Enter returns to the message); Ctrl+F jumps to it. */
    @Inject(method = "keyTyped", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsSearchKeys(char typed, int key, CallbackInfo ci) {
        if (ladsSearch == null || key == Keyboard.KEY_ESCAPE) return;
        if (ladsSearch.isFocused()) {
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) ladsFocusSearch(false);
            else if (ladsSearch.textboxKeyTyped(typed, key) && ChatTweaks.setQuery(ladsSearch.getText())) ChatTweaks189.refilter();
            ci.cancel();
        } else if (key == Keyboard.KEY_F && isCtrlKeyDown()) {
            ladsFocusSearch(true);
            ci.cancel();
        }
    }

    /** Shift+Enter Keeps Chat Open: the message is sent (and kept in the history) and the chat stays open, empty. */
    @WrapWithCondition(method = "keyTyped", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;displayGuiScreen(Lnet/minecraft/client/gui/GuiScreen;)V", ordinal = 1), require = 1)
    private boolean ladsKeepOpen(Minecraft minecraft, GuiScreen screen) {
        if (!ChatTweaks.keepOpen || !isShiftKeyDown()) return true;
        inputField.setText("");
        sentHistoryCursor = minecraft.ingameGUI.getChatGUI().getSentMessages().size();
        minecraft.ingameGUI.getChatGUI().resetScroll();
        return false;
    }

    /** Shortcuts: the typed line's first word expands ("/l" to "/lobby") before it is sent and kept in the history. */
    @ModifyArg(method = "keyTyped", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiChat;sendChatMessage(Ljava/lang/String;)V"), require = 1)
    private String ladsShortcut(String typed) {
        return ChatTweaks.expand(typed);
    }
}
