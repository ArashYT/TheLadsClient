package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.client.ChatAnimation;
import com.thelads.core.client.ChatHeads;
import com.thelads.core.v1_8_9.feature.Chat189;
import com.thelads.core.v1_8_9.feature.ChatHeads189;
import com.thelads.core.v1_8_9.feature.Options189;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.IChatComponent;
import com.thelads.core.client.ChatHistory;
import java.util.List;
import net.minecraft.util.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The Chat module, as 1.21.11 ChatMixin: size, line backgrounds, the newest message's slide and fade, and (Chat189) timestamps and
 * screenshot buttons. Display only. A message's lines share the tick it arrived in, which keys its animation. Also Chat Heads
 * (ChatHeads189): each message's sender found as it arrives, kept on its lines, drawn before its first line.
 */
@Mixin(GuiNewChat.class)
public abstract class GuiNewChatMixin implements Chat189.History {
    @Unique private final ChatAnimation ladsAnimation = new ChatAnimation();
    @Unique private boolean ladsAnimating, ladsLinePushed, ladsFirstLine;
    @Unique private int ladsLine, ladsClickOffset, ladsClickAt, ladsSenderAt, ladsRewrappedAt;
    @Unique private float ladsFade = 1f;
    @Shadow @Final private List<ChatLine> chatLines;
    @Shadow @Final private List<ChatLine> drawnChatLines;
    @Shadow private int scrollPos;
    @Shadow public abstract int getLineCount();
    @Shadow public abstract int getChatWidth();
    @Shadow public abstract float getChatScale();
    @Shadow public abstract void resetScroll();
    @Shadow public abstract void refreshChat();
    @Unique private ChatLine ladsDrawn;
    @Unique private NetworkPlayerInfo ladsSender, ladsRewrapped;
    /** Infinite History (ChatHistory): chatLines[0, ladsLaidOut) have their lines in drawnChatLines; -1 while every message has (vanilla). */
    @Unique private int ladsLaidOut = -1;

    @Override public int ladsScrollPos() { return scrollPos; }
    @Override public int ladsDrawnLines() { return drawnChatLines.size(); }
    @Override public int ladsMessages() { return chatLines.size(); }

    /** Infinite History: the 100-message and 100-line caps become the ceiling. */
    @ModifyConstant(method = "setChatLine", constant = @Constant(intValue = 100), require = 2)
    private int ladsHistoryCap(int vanilla) {
        return ChatHistory.limit(vanilla);
    }

    @Inject(method = "setChatLine", at = @At("TAIL"), require = 1)
    private void ladsNewMessageLaidOut(IChatComponent message, int id, int updateCounter, boolean displayOnly, CallbackInfo ci) {
        if (!displayOnly && ladsLaidOut >= 0) ladsLaidOut = Math.min(ladsLaidOut + 1, chatLines.size());
    }

    /** Vanilla drops the first message with this id from chatLines: one fewer laid out if it was among them. */
    @Inject(method = "deleteChatLine", at = @At("HEAD"), require = 1)
    private void ladsDeletedMessage(int id, CallbackInfo ci) {
        for (int i = 0; i < chatLines.size() && i < ladsLaidOut; i++)
            if (chatLines.get(i).getChatLineID() == id) { ladsLaidOut--; return; }
    }

    /** A resize or a chat setting lays out only the lines near the newest message, not the whole history. */
    @Inject(method = "refreshChat", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsLazyRefresh(CallbackInfo ci) {
        if (!ChatHistory.infinite()) {
            ladsLaidOut = -1;
            return;
        }
        ci.cancel();
        drawnChatLines.clear();
        resetScroll();
        ladsLaidOut = 0;
        ladsLayOut(getLineCount() + ChatHistory.AHEAD);
    }

    @Inject(method = "scroll", at = @At("HEAD"), require = 1)
    private void ladsLayOutOlder(int amount, CallbackInfo ci) {
        if (ChatHistory.infinite()) ladsLayOut(scrollPos + amount + getLineCount() + ChatHistory.AHEAD);
    }

    @Unique
    private void ladsLayOut(int lines) {
        if (ladsLaidOut < 0) return;
        ladsLaidOut = Chat189.layOut(chatLines, ladsLaidOut, drawnChatLines, lines, MathHelper.floor_float(getChatWidth() / getChatScale()));
    }

    @ModifyVariable(method = "printChatMessageWithOptionalDeletion", at = @At("HEAD"), argsOnly = true, require = 1)
    private IChatComponent ladsMessage(IChatComponent message) {
        return Chat189.message(message);
    }

    /** Raised moves chat up (Raised189, Forge's chat event); the line under the pointer is that much lower in screen pixels. */
    @ModifyVariable(method = "getChatComponent", at = @At("HEAD"), argsOnly = true, ordinal = 1, require = 1)
    private int ladsRaisedChat(int mouseY) {
        return mouseY - com.thelads.core.v1_8_9.feature.Raised189.chat() * new net.minecraft.client.gui.ScaledResolution(net.minecraft.client.Minecraft.getMinecraft()).getScaleFactor();
    }

    @Inject(method = "setChatLine", at = @At("HEAD"), require = 1)
    private void ladsNewestMessage(IChatComponent message, int id, int updateCounter, boolean displayOnly, CallbackInfo ci) {
        if (!displayOnly) ladsAnimation.start(updateCounter);
        ChatHeads.Match<NetworkPlayerInfo> match = displayOnly ? null : ChatHeads189.sender(message);
        ladsSender = displayOnly ? ladsRewrapped : match == null ? null : match.player();
        ladsSenderAt = displayOnly ? ladsRewrappedAt : match == null ? 0 : match.at();
        ladsRewrapped = null;
        ladsFirstLine = true;
    }

    /** Chat Heads: refreshChat re-wraps a stored message with the sender it arrived with. */
    @Redirect(method = "refreshChat", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/ChatLine;getChatComponent()Lnet/minecraft/util/IChatComponent;"), require = 1)
    private IChatComponent ladsRewrap(ChatLine line) {
        ladsRewrapped = ((ChatHeads189.Line) line).ladsHead();
        ladsRewrappedAt = ((ChatHeads189.Line) line).ladsAt();
        return line.getChatComponent();
    }

    @ModifyArg(method = "setChatLine", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiUtilRenderComponents;splitText(Lnet/minecraft/util/IChatComponent;ILnet/minecraft/client/gui/FontRenderer;ZZ)Ljava/util/List;"), index = 1, require = 1)
    private int ladsRoomForHead(int width) {
        return width - ChatHeads189.offset(ladsSender);
    }

    /** The drawn lines (first one marked) and the stored message keep the sender. */
    @Redirect(method = "setChatLine", at = @At(value = "INVOKE", target = "Ljava/util/List;add(ILjava/lang/Object;)V"), require = 2)
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void ladsLineSender(List lines, int index, Object line) {
        ((ChatHeads189.Line) line).ladsHead(ladsSender, ladsSenderAt, ladsFirstLine);
        ladsFirstLine = false;
        lines.add(index, line);
    }

    /**
     * Clicks and hovers find the text where it is drawn, after the head: getChatComponent adds up its parts' widths, and the part
     * holding the head's place (Before name: the sender's name on the first line; Start of line: the first part) gets its width.
     */
    @Redirect(method = "getChatComponent", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/ChatLine;getChatComponent()Lnet/minecraft/util/IChatComponent;"), require = 1)
    private IChatComponent ladsPointedLine(ChatLine line) {
        ChatHeads189.Line head = (ChatHeads189.Line) line;
        boolean beforeName = ChatHeads.beforeName();
        ladsClickOffset = beforeName && !head.ladsFirst() ? 0 : ChatHeads189.offset(head.ladsHead());
        ladsClickAt = beforeName ? head.ladsAt() : 0;
        return line.getChatComponent();
    }

    @Redirect(method = "getChatComponent", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/FontRenderer;getStringWidth(Ljava/lang/String;)I"), require = 1)
    private int ladsPointedWidth(FontRenderer font, String text) {
        int width = font.getStringWidth(text);
        if (ladsClickOffset > 0 && (ladsClickAt -= ChatHeads.visibleLength(text)) < 0) {
            width += ladsClickOffset;
            ladsClickOffset = 0;
        }
        return width;
    }

    @Inject(method = "calculateChatboxWidth", at = @At("HEAD"), cancellable = true, require = 1)
    private static void ladsChatWidth(float scale, CallbackInfoReturnable<Integer> cir) {
        if (Options189.enabled("Chat")) cir.setReturnValue((int) Options189.number("Chat", "Chat Width", 320));
    }

    @Inject(method = "calculateChatboxHeight", at = @At("HEAD"), cancellable = true, require = 1)
    private static void ladsChatHeight(float scale, CallbackInfoReturnable<Integer> cir) {
        if (Options189.enabled("Chat")) cir.setReturnValue((int) Options189.number("Chat", "Chat Height", 180));
    }

    @Inject(method = "drawChat", at = @At("HEAD"), require = 1)
    private void ladsBeginChat(int updateCounter, CallbackInfo ci) {
        if (ChatHeads189.layoutChanged()) refreshChat();
        ladsAnimating = Options189.enabled("Chat") && Options189.bool("Chat", "Message Animations", true) && ladsAnimation.running();
    }

    /** The line drawChat is about to draw: its age is read first. */
    @Redirect(method = "drawChat", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/ChatLine;getUpdatedCounter()I"), require = 1)
    private int ladsLine(ChatLine line) {
        ladsDrawn = line;
        return ladsLine = line.getUpdatedCounter();
    }

    /** Each drawn line starts with its background; the newest message's lines get their own pushed pose and fade until their text. */
    @Redirect(method = "drawChat", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiNewChat;drawRect(IIIII)V", ordinal = 0), require = 1)
    private void ladsLineBackground(int left, int top, int right, int bottom, int color) {
        float progress = ladsAnimating ? ladsAnimation.progress(ladsLine) : 1f;
        if (progress < 1f) {
            ladsFade = ChatAnimation.fade(progress);
            GlStateManager.pushMatrix();
            GlStateManager.translate(ChatAnimation.slide(progress), 0.0F, 0.0F);
            ladsLinePushed = true;
            Chat189.animated++;
        }
        if (!Options189.enabled("Chat") || Options189.bool("Chat", "Chat Background", true)) Gui.drawRect(left, top, right, bottom, ladsFaded(color));
    }

    @Redirect(method = "drawChat", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/FontRenderer;drawStringWithShadow(Ljava/lang/String;FFI)I"), require = 1)
    private int ladsLineText(FontRenderer font, String text, float x, float y, int color) {
        // Chat Heads (ChatHeads189); every part keeps the Chat module's Text Shadow, on by default as in vanilla.
        boolean shadow = ChatHistory.shadow();
        ChatHeads189.Line line = (ChatHeads189.Line) ladsDrawn;
        NetworkPlayerInfo head = line == null ? null : line.ladsHead();
        int offset = ChatHeads189.offset(head), width;
        if (offset > 0 && ChatHeads.beforeName()) {
            if (head != null && line.ladsFirst()) {
                // Before name: the text before the sender's name, the head, then the rest of the line after the head.
                int split = ChatHeads.split(text, line.ladsAt());
                String before = text.substring(0, split);
                float headX = x + font.getStringWidth(before);
                font.drawString(before, x, y, ladsFaded(color), shadow);
                ChatHeads189.draw(head, (int) headX, (int) y, ladsFaded(color) >>> 24);
                width = font.drawString(FontRenderer.getFormatFromString(before) + text.substring(split), headX + offset, y, ladsFaded(color), shadow);
            } else width = font.drawString(text, x, y, ladsFaded(color), shadow);
        } else {
            if (offset > 0 && head != null && line.ladsFirst())
                ChatHeads189.draw(head, (int) x, (int) y, ladsFaded(color) >>> 24);
            width = font.drawString(text, x + offset, y, ladsFaded(color), shadow);
        }
        if (ladsLinePushed) {
            ladsLinePushed = false;
            GlStateManager.popMatrix();
        }
        return width;
    }

    @Unique
    private int ladsFaded(int color) {
        if (!ladsLinePushed) return color;
        // 1.8.9's FontRenderer draws alpha below 4 as opaque, so the faded line never drops under 4.
        return Math.max(4, Math.round((color >>> 24) * ladsFade)) << 24 | color & 0xFFFFFF;
    }
}
