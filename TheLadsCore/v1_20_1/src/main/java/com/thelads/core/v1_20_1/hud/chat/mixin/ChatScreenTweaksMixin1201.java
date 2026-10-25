package com.thelads.core.v1_20_1.hud.chat.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.client.ChatTweaks;
import com.thelads.core.v1_20_1.hud.chat.ChatTweaks1201;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The chat screen's parity-1023 tweaks on 1.20.1 (ChatTweaks1201): the tab row and the search box just above the input bar (Ctrl+F
 * focuses the search, Enter goes back to the message), Shift+Enter sends and keeps the chat open, Shortcuts expand the typed line,
 * and Unlimited Command Length lifts the 256-character input and trim in singleplayer only.
 */
@Mixin(ChatScreen.class)
public abstract class ChatScreenTweaksMixin1201 extends Screen {
    @Shadow protected EditBox input;
    @Shadow private int historyPos;
    @Unique private EditBox ladsSearch;

    private ChatScreenTweaksMixin1201(Component title) { super(title); }

    @Inject(method = "init", at = @At("TAIL"), require = 1)
    private void lads$init(CallbackInfo ci) {
        if (ChatTweaks.longCommands(minecraft.hasSingleplayerServer())) input.setMaxLength(ChatTweaks.LONG_COMMAND_LIMIT);
        ladsSearch = null;
        if (!ChatTweaks.search) return;
        Component hint = Component.literal("Search chat");
        ladsSearch = new EditBox(font, width - 2 - ChatTweaks.SEARCH_WIDTH, height - ChatTweaks.BAR_BOTTOM - ChatTweaks.BAR_HEIGHT,
            ChatTweaks.SEARCH_WIDTH, ChatTweaks.BAR_HEIGHT, hint);
        ladsSearch.setHint(hint.copy().withStyle(ChatFormatting.DARK_GRAY));
        ladsSearch.setValue(ChatTweaks.query());
        ladsSearch.setResponder(text -> { if (ChatTweaks.setQuery(text)) ChatTweaks1201.refilter(); });
        addRenderableWidget(ladsSearch);
    }

    @Inject(method = "removed", at = @At("TAIL"), require = 1)
    private void lads$closed(CallbackInfo ci) {
        if (ChatTweaks.setQuery("")) ChatTweaks1201.refilter();
    }

    /** Drawn first, so a command's suggestion list (drawn last, over this row) stays on top. */
    @Inject(method = "render", at = @At("HEAD"), require = 1)
    private void lads$tabs(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        int y = height - ChatTweaks.BAR_BOTTOM - ChatTweaks.BAR_HEIGHT, x = 2;
        for (int i = 0; i < ChatTweaks.tabCount(); i++) {
            String name = ChatTweaks.tabName(i);
            int w = font.width(name) + 8;
            boolean active = i == ChatTweaks.activeTab(), hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + ChatTweaks.BAR_HEIGHT;
            graphics.fill(x, y, x + w, y + ChatTweaks.BAR_HEIGHT, active ? 0xC0000000 : hover ? 0xA0000000 : 0x60000000);
            graphics.drawString(font, name, x + 4, y + 2, active ? 0xFFFFFF55 : 0xFFC0C0C0, false);
            x += w + 2;
        }
        if (!ChatTweaks.buttons) return;
        ChatTweaks1201.Access chat = (ChatTweaks1201.Access) minecraft.gui.getChat();
        if (mouseX < 0 || chat.lads$hovered(mouseY) == null || mouseX >= chat.lads$hoverRight() + 3 * ChatTweaks.BUTTON + 2) return;
        int right = chat.lads$hoverRight(), top = chat.lads$hoverTop(), button = ChatTweaks.button(mouseX, right);
        for (int i = 0; i < 3; i++) {
            int left = right + i * (ChatTweaks.BUTTON + 1);
            graphics.fill(left, top, left + ChatTweaks.BUTTON, top + 9, button == i ? 0xE0303030 : 0xA0000000);
            graphics.drawString(font, i == 0 ? ChatTweaks.COPY_LABEL : i == 1 ? ChatTweaks.DELETE_LABEL : ChatTweaks.IMAGE_LABEL, left + 3, top + 1, button == i ? 0xFFFFFF55 : 0xFFC0C0C0, false);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$click(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (ladsSearch != null && ladsSearch.isFocused() && !ladsSearch.isMouseOver(mouseX, mouseY)) setFocused(input);
        if (input.getValue().startsWith("/")) return; // a command's suggestion list may cover the tabs and buttons: it gets the click
        if (ChatTweaks.buttons && button == 0) {
            ChatTweaks1201.Access chat = (ChatTweaks1201.Access) minecraft.gui.getChat();
            net.minecraft.client.GuiMessage message = chat.lads$hovered(mouseY);
            int which = message == null ? -1 : ChatTweaks.button((int) mouseX, chat.lads$hoverRight());
            if (which >= 0) {
                ChatTweaks1201.messageButton(message, which);
                cir.setReturnValue(true);
                return;
            }
        }
        int y = height - ChatTweaks.BAR_BOTTOM - ChatTweaks.BAR_HEIGHT, x = 2;
        if (button != 0 || mouseY < y || mouseY >= y + ChatTweaks.BAR_HEIGHT) return;
        for (int i = 0; i < ChatTweaks.tabCount(); i++) {
            int w = font.width(ChatTweaks.tabName(i)) + 8;
            if (mouseX >= x && mouseX < x + w) {
                if (ChatTweaks.selectTab(i)) ChatTweaks1201.refilter();
                cir.setReturnValue(true);
                return;
            }
            x += w + 2;
        }
    }

    /** Typing in the search box: every key goes to it (Enter returns to the message); Ctrl+F jumps to it. */
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$searchKeys(int key, int scancode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (ladsSearch == null || key == GLFW.GLFW_KEY_ESCAPE) return;
        if (ladsSearch.isFocused()) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) setFocused(input);
            else ladsSearch.keyPressed(key, scancode, modifiers);
            cir.setReturnValue(true);
        } else if (key == GLFW.GLFW_KEY_F && hasControlDown()) {
            setFocused(ladsSearch);
            cir.setReturnValue(true);
        }
    }

    /** Shift+Enter Keeps Chat Open: the message is sent (and kept in the history) and the chat stays open, empty. */
    @WrapWithCondition(method = "keyPressed", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V", ordinal = 1), require = 1)
    private boolean lads$keepOpen(Minecraft mc, Screen screen) {
        if (!ChatTweaks.keepOpen || !hasShiftDown()) return true;
        input.setValue("");
        historyPos = mc.gui.getChat().getRecentChat().size();
        mc.gui.getChat().resetChatScroll();
        return false;
    }

    /** Shortcuts: the typed line's first word expands ("/l" to "/lobby") before it is sent and kept in the history. */
    @ModifyVariable(method = "handleChatInput", at = @At("HEAD"), argsOnly = true, require = 1)
    private String lads$shortcut(String typed) {
        return ChatTweaks.expand(typed);
    }

    /** Unlimited Command Length: a command is not cut at 256 characters in singleplayer (the game's own server takes it). */
    @WrapOperation(method = "normalizeChatMessage", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/StringUtil;trimChatMessage(Ljava/lang/String;)Ljava/lang/String;"), require = 1)
    private String lads$longCommand(String message, Operation<String> original) {
        return message.startsWith("/") && ChatTweaks.longCommands(minecraft.hasSingleplayerServer()) ? message : original.call(message);
    }
}
