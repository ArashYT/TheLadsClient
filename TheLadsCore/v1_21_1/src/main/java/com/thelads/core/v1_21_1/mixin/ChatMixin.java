package com.thelads.core.v1_21_1.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.thelads.core.client.ChatAnimation;
import com.thelads.core.v1_21_1.feature.NativeNicknames;
import com.thelads.core.v1_21_1.feature.NativeQualityOfLife;
import net.minecraft.ChatFormatting;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The Chat module, as on 26.x. Modifies display only: signed packets, signatures and report logs stay intact. */
@Mixin(ChatComponent.class)
public abstract class ChatMixin {
    @Unique private static final String RENDER = "render(Lnet/minecraft/client/gui/GuiGraphics;IIIZ)V";
    @Unique private final ChatAnimation ladsAnimation = new ChatAnimation();
    @Unique private boolean ladsAnimating;
    @Unique private float ladsFade = 1f;
    @Unique private boolean ladsLinePushed;

    /** Nametags renames, rewritten once as a message arrives. */
    @ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
        at = @At("HEAD"), argsOnly = true, require = 1)
    private Component ladsRename(Component content) {
        return NativeNicknames.rename(content);
    }

    @ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
        at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 1)
    private Component ladsTimestamp(Component content) {
        if (!NativeQualityOfLife.enabled("Chat") || !NativeQualityOfLife.bool("Chat", "Timestamps", false)) return content;
        var prefix = Component.literal("[" + java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) + "] ")
            .withStyle(ChatFormatting.GRAY);
        // Unstyled root so the original message keeps its colour/click/hover style.
        return Component.empty().append(prefix).append(content);
    }

    @ModifyArg(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;addMessageToDisplayQueue(Lnet/minecraft/client/GuiMessage;)V"), require = 1)
    private GuiMessage ladsNewestMessage(GuiMessage message) {
        ladsAnimation.start(message.addedTime());
        return message;
    }

    @Inject(method = "getWidth(D)I", at = @At("HEAD"), cancellable = true, require = 1)
    private static void ladsChatWidth(double widthOption, CallbackInfoReturnable<Integer> cir) {
        if (NativeQualityOfLife.enabled("Chat")) cir.setReturnValue((int) NativeQualityOfLife.number("Chat", "Chat Width", 320));
    }

    @Inject(method = "getHeight(D)I", at = @At("HEAD"), cancellable = true, require = 1)
    private static void ladsChatHeight(double heightOption, CallbackInfoReturnable<Integer> cir) {
        if (NativeQualityOfLife.enabled("Chat")) cir.setReturnValue((int) NativeQualityOfLife.number("Chat", "Chat Height", 180));
    }

    /** Second option read in render is textBackgroundOpacity; zero hides only the black line boxes. */
    @ModifyExpressionValue(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;", ordinal = 1), require = 1)
    private Object ladsChatBackground(Object opacity) {
        return NativeQualityOfLife.enabled("Chat") && !NativeQualityOfLife.bool("Chat", "Chat Background", true) ? (Object) 0.0D : opacity;
    }

    @Inject(method = RENDER, at = @At("HEAD"), require = 1)
    private void ladsBeginChat(GuiGraphics graphics, int tick, int mouseX, int mouseY, boolean focused, CallbackInfo ci) {
        ladsAnimating = NativeQualityOfLife.enabled("Chat") && NativeQualityOfLife.bool("Chat", "Message Animations", true) && ladsAnimation.running();
    }

    /**
     * Each drawn line starts with its background fill. Only the newest message's lines get their own pushed pose (slide)
     * and alpha (fade), popped right after that line's text; nothing else on the HUD moves.
     */
    @WrapOperation(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V", ordinal = 0), require = 1)
    private void ladsLineBackground(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color, Operation<Void> fill, @Local GuiMessage.Line line) {
        float progress = ladsAnimating ? ladsAnimation.progress(line.addedTime()) : 1f;
        if (progress < 1f) {
            ladsFade = ChatAnimation.fade(progress);
            graphics.pose().pushPose();
            graphics.pose().translate(ChatAnimation.slide(progress), 0f, 0f);
            ladsLinePushed = true;
        }
        fill.call(graphics, x0, y0, x1, y1, ladsFaded(color));
    }

    @ModifyArg(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)I"), index = 4, require = 1)
    private int ladsLineText(int color) { return ladsFaded(color); }

    @Inject(method = RENDER, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V", ordinal = 0, shift = At.Shift.AFTER), require = 1)
    private void ladsLineDone(GuiGraphics graphics, int tick, int mouseX, int mouseY, boolean focused, CallbackInfo ci) {
        if (!ladsLinePushed) return;
        ladsLinePushed = false;
        graphics.pose().popPose();
    }

    @Unique
    private int ladsFaded(int color) {
        if (!ladsLinePushed) return color;
        // 1.21.1's Font draws alpha below 4 as opaque, so the faded line never drops under 4.
        int alpha = Math.max(4, Math.round((color >>> 24) * ladsFade));
        return alpha << 24 | color & 0xFFFFFF;
    }
}
