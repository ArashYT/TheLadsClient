package com.thelads.core.v1_21_11.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.thelads.core.client.ChatAnimation;
import com.thelads.core.v1_21_11.feature.NativeQualityOfLife;
import net.minecraft.ChatFormatting;
import net.minecraft.client.GuiMessage;
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
    @Unique private static final String RENDER = "render(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IIZ)V";
    @Unique private static final String LINE = "Lnet/minecraft/client/gui/components/ChatComponent$LineConsumer;accept(Lnet/minecraft/client/GuiMessage$Line;IF)V";
    @Unique private final ChatAnimation ladsAnimation = new ChatAnimation();
    @Unique private ChatComponent.ChatGraphicsAccess ladsGraphics;
    @Unique private float ladsSlide;

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

    /** Second option read in the render body is textBackgroundOpacity; zero hides only the black line boxes. */
    @ModifyExpressionValue(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;", ordinal = 1), require = 1)
    private Object ladsChatBackground(Object opacity) {
        return NativeQualityOfLife.enabled("Chat") && !NativeQualityOfLife.bool("Chat", "Chat Background", true) ? (Object) 0.0D : opacity;
    }

    @Inject(method = RENDER, at = @At("HEAD"), require = 1)
    private void ladsBeginChat(ChatComponent.ChatGraphicsAccess graphics, int screenHeight, int ticks, boolean focused, CallbackInfo ci) {
        boolean animate = NativeQualityOfLife.enabled("Chat") && NativeQualityOfLife.bool("Chat", "Message Animations", true);
        ladsGraphics = animate && ladsAnimation.running() ? graphics : null;
    }

    @Inject(method = RENDER, at = @At("RETURN"), require = 1)
    private void ladsEndChat(CallbackInfo ci) { ladsGraphics = null; }

    /** Only the newest message's lines slide and fade in, inside the chat's own pose; nothing else on the HUD moves. */
    @ModifyArg(method = "forEachLine", at = @At(value = "INVOKE", target = LINE), index = 2, require = 1)
    private float ladsAnimateLine(GuiMessage.Line line, int index, float alpha) {
        float progress = ladsGraphics == null ? 1f : ladsAnimation.progress(line.addedTime());
        if (progress >= 1f) return alpha;
        float slide = ladsSlide = ChatAnimation.slide(progress);
        ladsGraphics.updatePose(pose -> pose.translate(slide, 0f));
        return alpha * ChatAnimation.fade(progress);
    }

    @Inject(method = "forEachLine", at = @At(value = "INVOKE", target = LINE, shift = At.Shift.AFTER), require = 1)
    private void ladsLineDone(CallbackInfoReturnable<Integer> cir) {
        if (ladsSlide == 0f) return;
        float slide = ladsSlide;
        ladsSlide = 0f;
        ladsGraphics.updatePose(pose -> pose.translate(-slide, 0f));
    }
}
