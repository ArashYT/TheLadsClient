package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.client.ChatAnimation;
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
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
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
public abstract class GuiNewChatMixin {
    @Unique private final ChatAnimation ladsAnimation = new ChatAnimation();
    @Unique private boolean ladsAnimating, ladsLinePushed, ladsFirstLine;
    @Unique private int ladsLine, ladsClickOffset;
    @Unique private float ladsFade = 1f;
    @Unique private ChatLine ladsDrawn;
    @Unique private NetworkPlayerInfo ladsSender, ladsRewrapped;

    @Shadow public abstract void refreshChat();

    @ModifyVariable(method = "printChatMessageWithOptionalDeletion", at = @At("HEAD"), argsOnly = true, require = 1)
    private IChatComponent ladsMessage(IChatComponent message) {
        return Chat189.message(message);
    }

    @Inject(method = "setChatLine", at = @At("HEAD"), require = 1)
    private void ladsNewestMessage(IChatComponent message, int id, int updateCounter, boolean displayOnly, CallbackInfo ci) {
        if (!displayOnly) ladsAnimation.start(updateCounter);
        ladsSender = displayOnly ? ladsRewrapped : ChatHeads189.sender(message);
        ladsRewrapped = null;
        ladsFirstLine = true;
    }

    /** Chat Heads: refreshChat re-wraps a stored message with the sender it arrived with. */
    @Redirect(method = "refreshChat", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/ChatLine;getChatComponent()Lnet/minecraft/util/IChatComponent;"), require = 1)
    private IChatComponent ladsRewrap(ChatLine line) {
        ladsRewrapped = ((ChatHeads189.Line) line).ladsHead();
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
        ((ChatHeads189.Line) line).ladsHead(ladsSender, ladsFirstLine);
        ladsFirstLine = false;
        lines.add(index, line);
    }

    /** Clicks and hovers find the text where it is drawn, after the head. */
    @Redirect(method = "getChatComponent", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/ChatLine;getChatComponent()Lnet/minecraft/util/IChatComponent;"), require = 1)
    private IChatComponent ladsPointedLine(ChatLine line) {
        ladsClickOffset = ChatHeads189.offset(((ChatHeads189.Line) line).ladsHead());
        return line.getChatComponent();
    }

    @Redirect(method = "getChatComponent", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/FontRenderer;getStringWidth(Ljava/lang/String;)I"), require = 1)
    private int ladsPointedWidth(FontRenderer font, String text) {
        int width = font.getStringWidth(text) + ladsClickOffset;
        ladsClickOffset = 0;
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
        ChatHeads189.Line line = (ChatHeads189.Line) ladsDrawn;
        NetworkPlayerInfo head = line == null ? null : line.ladsHead();
        int offset = ChatHeads189.offset(head);
        if (offset > 0 && head != null && line.ladsFirst())
            ChatHeads189.draw(head, (int) x, (int) y, ladsFaded(color) >>> 24);
        int width = font.drawStringWithShadow(text, x + offset, y, ladsFaded(color));
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
