package com.thelads.core.v26_2.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.thelads.core.client.ChatAnimation;
import com.thelads.core.v26_2.feature.NativeClientTools;
import com.thelads.core.v26_2.feature.NativeNicknames;
import com.thelads.core.v26_2.feature.NativeQualityOfLife;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The Chat module. Modifies display only: signed packets, signatures and report logs stay intact. */
@Mixin(ChatComponent.class)
public abstract class ClientToolsChatMixin {
    @Unique private static final String RENDER = "extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V";
    @Unique private static final String LINE = "Lnet/minecraft/client/gui/components/ChatComponent$LineConsumer;accept(Lnet/minecraft/client/multiplayer/chat/GuiMessage$Line;IF)V";
    @Unique private final ChatAnimation ladsAnimation = new ChatAnimation();
    @Unique private ChatComponent.ChatGraphicsAccess ladsGraphics;
    @Unique private float ladsSlide;

    @ModifyVariable(method = {"addClientSystemMessage", "addServerSystemMessage", "addPlayerMessage"},
        at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 3)
    private Component ladsTimestamp(Component content) {
        return NativeClientTools.timestamp(content);
    }

    /** Nametags renames, rewritten once as a message arrives. */
    @ModifyVariable(method = "addMessage", at = @At("HEAD"), argsOnly = true, require = 1)
    private Component ladsRename(Component content) {
        return NativeNicknames.rename(content);
    }

    @ModifyArg(method = "addMessage", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;addMessageToDisplayQueue(Lnet/minecraft/client/multiplayer/chat/GuiMessage;)V"), require = 1)
    private GuiMessage ladsNewestMessage(GuiMessage message) {
        ladsAnimation.start(message);
        return message;
    }

    @Inject(method = "getWidth(D)I", at = @At("HEAD"), cancellable = true)
    private static void ladsChatWidth(double widthOption, CallbackInfoReturnable<Integer> cir) {
        if (NativeQualityOfLife.enabled("Chat")) {
            cir.setReturnValue((int) NativeQualityOfLife.number("Chat", "Chat Width", 320));
        }
    }

    @Inject(method = "getHeight(D)I", at = @At("HEAD"), cancellable = true)
    private static void ladsChatHeight(double heightOption, CallbackInfoReturnable<Integer> cir) {
        if (NativeQualityOfLife.enabled("Chat")) {
            cir.setReturnValue((int) NativeQualityOfLife.number("Chat", "Chat Height", 180));
        }
    }

    /** Second option read in the render body is textBackgroundOpacity; zero hides only the black line boxes. */
    @ModifyExpressionValue(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;", ordinal = 1), require = 1)
    private Object ladsChatBackground(Object opacity) {
        return NativeQualityOfLife.enabled("Chat") && !NativeQualityOfLife.bool("Chat", "Chat Background", true) ? (Object) 0.0D : opacity;
    }

    @Inject(method = RENDER, at = @At("HEAD"), require = 1)
    private void ladsBeginChat(ChatComponent.ChatGraphicsAccess graphics, int screenHeight, int ticks, ChatComponent.DisplayMode mode, CallbackInfo ci) {
        boolean animate = NativeQualityOfLife.enabled("Chat") && NativeQualityOfLife.bool("Chat", "Message Animations", true);
        ladsGraphics = animate && ladsAnimation.running() ? graphics : null;
    }

    @Inject(method = RENDER, at = @At("RETURN"), require = 1)
    private void ladsEndChat(CallbackInfo ci) { ladsGraphics = null; }

    /** Raised: chat lays out above a shorter screen, so it is drawn, hovered and clicked that much higher. */
    @ModifyVariable(method = RENDER, at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 1)
    private int ladsRaiseChat(int screenHeight) { return screenHeight - com.thelads.core.v26_2.feature.Raised26.chat(); }

    /** Only the newest message's lines slide and fade in, inside the chat's own pose; nothing else on the HUD moves. */
    @ModifyArg(method = "forEachLine", at = @At(value = "INVOKE", target = LINE), index = 2, require = 1)
    private float ladsAnimateLine(GuiMessage.Line line, int index, float alpha) {
        float progress = ladsGraphics == null ? 1f : ladsAnimation.progress(line.parent());
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
