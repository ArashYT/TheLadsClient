package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.client.ChatAnimation;
import com.thelads.core.v1_8_9.feature.Chat189;
import com.thelads.core.v1_8_9.feature.Options189;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiNewChat;
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
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The Chat module, as 1.21.11 ChatMixin: size, line backgrounds, the newest message's slide and fade, and (Chat189) timestamps and
 * screenshot buttons. Display only. A message's lines share the tick it arrived in, which keys its animation.
 */
@Mixin(GuiNewChat.class)
public abstract class GuiNewChatMixin implements Chat189.History {
    @Unique private final ChatAnimation ladsAnimation = new ChatAnimation();
    @Unique private boolean ladsAnimating, ladsLinePushed;
    @Unique private int ladsLine;
    @Unique private float ladsFade = 1f;
    @Shadow @Final private List<ChatLine> chatLines;
    @Shadow @Final private List<ChatLine> drawnChatLines;
    @Shadow private int scrollPos;
    @Shadow public abstract int getLineCount();
    @Shadow public abstract int getChatWidth();
    @Shadow public abstract float getChatScale();
    @Shadow public abstract void resetScroll();
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

    @Inject(method = "setChatLine", at = @At("HEAD"), require = 1)
    private void ladsNewestMessage(IChatComponent message, int id, int updateCounter, boolean displayOnly, CallbackInfo ci) {
        if (!displayOnly) ladsAnimation.start(updateCounter);
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
        ladsAnimating = Options189.enabled("Chat") && Options189.bool("Chat", "Message Animations", true) && ladsAnimation.running();
    }

    /** The line drawChat is about to draw: its age is read first. */
    @Redirect(method = "drawChat", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/ChatLine;getUpdatedCounter()I"), require = 1)
    private int ladsLine(ChatLine line) {
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
        // The Chat module's Text Shadow, on by default as in vanilla.
        int width = font.drawString(text, x, y, ladsFaded(color), ChatHistory.shadow());
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
