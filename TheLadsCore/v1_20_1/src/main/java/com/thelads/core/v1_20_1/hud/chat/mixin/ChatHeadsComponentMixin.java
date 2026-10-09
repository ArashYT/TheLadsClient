package com.thelads.core.v1_20_1.hud.chat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.thelads.core.client.ChatHeads;
import com.thelads.core.client.ChatHistory;
import com.thelads.core.v1_20_1.hud.chat.NativeChatHeads1201;
import java.util.List;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Chat Heads (NativeChatHeads1201) in ChatComponent. A message is wrapped narrower by the room its head takes and its GuiMessage and
 * lines keep the sender; the line pass draws the head just before the sender's name on a message's first line (the text before the
 * name, the head, then the rest of the line moved right by the head) or at the start of the line, with every line of the message
 * moved right; clicks and hovers find the text where it is drawn. Also draws the chat lines with the Chat module's Text Shadow.
 */
@Mixin(ChatComponent.class)
public abstract class ChatHeadsComponentMixin {
    @Unique private static final String ADD5 = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;ILnet/minecraft/client/GuiMessageTag;Z)V";

    @Shadow @Final private List<GuiMessage.Line> trimmedMessages;
    @Shadow private int chatScrollbarPos;
    @Shadow public abstract int getLinesPerPage();
    @Shadow public abstract void rescaleChat();

    /** A stored message wrapped again (resize, chat setting): its lines get the sender it arrived with. */
    @WrapOperation(method = "refreshTrimmedMessage", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/GuiMessage;content()Lnet/minecraft/network/chat/Component;"), require = 1)
    private Component lads$rewrap(GuiMessage message, Operation<Component> original) {
        NativeChatHeads1201.rewrap(message);
        return original.call(message);
    }

    @Inject(method = ADD5, at = @At("RETURN"), require = 1)
    private void lads$wrapped(Component content, MessageSignature signature, int addedTime, net.minecraft.client.GuiMessageTag tag, boolean displayOnly, CallbackInfo ci) {
        NativeChatHeads1201.done();
    }

    @ModifyArg(method = ADD5, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ComponentRenderUtils;wrapComponents(Lnet/minecraft/network/chat/FormattedText;ILnet/minecraft/client/gui/Font;)Ljava/util/List;"), index = 1, require = 1)
    private int lads$roomForHead(int width) {
        return width - NativeChatHeads1201.wrapOffset();
    }

    /** The GuiMessage and each of its lines are stored with the sender (the two list adds in the method). */
    @ModifyArg(method = ADD5, at = @At(value = "INVOKE", target = "Ljava/util/List;add(ILjava/lang/Object;)V"), index = 1, require = 2)
    private Object lads$stamp(Object created) {
        return NativeChatHeads1201.stamp(created);
    }

    /** The module, Position or Keep text aligned changed: lines are wrapped again. */
    @Inject(method = "render", at = @At("HEAD"), require = 1)
    private void lads$headsLayout(CallbackInfo ci) {
        if (NativeChatHeads1201.layoutChanged()) rescaleChat();
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)I"), require = 1)
    private int lads$lineText(GuiGraphics graphics, Font font, FormattedCharSequence text, int x, int y, int color, Operation<Integer> original,
                              @Local(ordinal = 0) GuiMessage.Line line) {
        int shift = NativeChatHeads1201.shift(line);
        if (shift == 0) return lads$text(graphics, font, text, x, y, color, original);
        int alpha = color >>> 24, headX = x;
        if (ChatHeads.beforeName()) {
            int at = NativeChatHeads1201.at(line, text);
            if (at > 0) {
                FormattedCharSequence before = NativeChatHeads1201.slice(text, 0, at);
                lads$text(graphics, font, before, x, y, color, original);
                headX = x + font.width(before);
                text = NativeChatHeads1201.slice(text, at, Integer.MAX_VALUE);
            }
            NativeChatHeads1201.draw(graphics, line, headX, y, alpha);
        } else if (NativeChatHeads1201.first(line) || lads$topLine(line)) {
            // Start of line: the message's first line, or the top line when the first one has scrolled off.
            NativeChatHeads1201.draw(graphics, line, x, y, alpha);
        }
        return lads$text(graphics, font, text, headX + shift, y, color, original);
    }

    @Unique
    private boolean lads$topLine(GuiMessage.Line line) {
        int top = Math.min(chatScrollbarPos + getLinesPerPage(), trimmedMessages.size()) - 1;
        return top >= 0 && trimmedMessages.get(top) == line;
    }

    @Unique
    private static int lads$text(GuiGraphics graphics, Font font, FormattedCharSequence text, int x, int y, int color, Operation<Integer> original) {
        return ChatHistory.shadow() ? original.call(graphics, font, text, x, y, color) : graphics.drawString(font, text, x, y, color, false);
    }

    /** Clicks and hovers find the text where it is drawn, after the head. */
    @WrapOperation(method = "getClickedComponentStyleAt", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/StringSplitter;componentStyleAtWidth(Lnet/minecraft/util/FormattedCharSequence;I)Lnet/minecraft/network/chat/Style;"), require = 1)
    private Style lads$styleAt(StringSplitter splitter, FormattedCharSequence text, int x, Operation<Style> original, @Local(ordinal = 0) GuiMessage.Line line) {
        int shift = NativeChatHeads1201.shift(line);
        if (shift == 0) return original.call(splitter, text, x);
        int before = 0;
        if (ChatHeads.beforeName()) {
            int at = NativeChatHeads1201.at(line, text);
            if (at > 0) {
                FormattedCharSequence start = NativeChatHeads1201.slice(text, 0, at);
                before = Minecraft.getInstance().font.width(start);
                if (x < before) return original.call(splitter, start, x);
                text = NativeChatHeads1201.slice(text, at, Integer.MAX_VALUE);
            }
        }
        x -= before;
        return x < shift ? null : original.call(splitter, text, x - shift);
    }

    /** The sign indicator's icon follows the (moved) text. */
    @Inject(method = "getTagIconLeft", at = @At("RETURN"), cancellable = true, require = 1)
    private void lads$iconAfterHead(GuiMessage.Line line, CallbackInfoReturnable<Integer> cir) {
        int shift = NativeChatHeads1201.shift(line);
        if (shift != 0) cir.setReturnValue(cir.getReturnValue() + shift);
    }
}
