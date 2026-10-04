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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
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
public abstract class GuiNewChatMixin {
    @Unique private final ChatAnimation ladsAnimation = new ChatAnimation();
    @Unique private boolean ladsAnimating, ladsLinePushed;
    @Unique private int ladsLine;
    @Unique private float ladsFade = 1f;

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
        int width = font.drawStringWithShadow(text, x, y, ladsFaded(color));
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
