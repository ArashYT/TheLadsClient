package com.thelads.core.v1_20_1.hud.chat.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.client.ChatAnimation;
import com.thelads.core.client.ChatHistory;
import com.thelads.core.v1_20_1.hud.Hud1201;
import com.thelads.core.v1_20_1.hud.Raised1201;
import com.thelads.core.v1_20_1.hud.chat.ChatFeatures1201;
import com.thelads.core.v1_20_1.hud.chat.NativeChatHeads1201;
import com.thelads.core.v1_20_1.hud.chat.NativeClientTools1201;
import com.thelads.core.v1_20_1.hud.chat.NativeNicknames1201;
import java.util.List;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The Chat module on 1.20.1 (the 26.3 ClientToolsChatMixin): size, line backgrounds, the newest message's slide and fade,
 * timestamps, Raised, Infinite History and Text Shadow. Display only: signed packets, signatures and report logs stay intact.
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    @Unique private static final String ADD3 = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V";
    @Unique private static final String ADD5 = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;ILnet/minecraft/client/GuiMessageTag;Z)V";
    @Unique private static final String DRAW_COMPONENT = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)I";

    @Unique private final ChatAnimation ladsAnimation = new ChatAnimation();
    @Unique private boolean ladsAnimating;
    @Unique private GuiMessage.Line ladsLine;
    @Unique private float ladsProgress = 1f;
    @Unique private int ladsWidthKey = Integer.MIN_VALUE;
    /** Infinite History: allMessages[0, ladsLaidOut) have their lines in trimmedMessages; -1 while every message has (vanilla). */
    @Unique private int ladsLaidOut = -1;
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private List<GuiMessage> allMessages;
    @Shadow @Final private List<GuiMessage.Line> trimmedMessages;
    @Shadow private int chatScrollbarPos;
    @Shadow public abstract int getWidth();
    @Shadow public abstract double getScale();
    @Shadow public abstract int getLinesPerPage();
    @Shadow public abstract void rescaleChat();

    /** Infinite History (ChatHistory): the 100-message and 100-line caps become the ceiling. */
    @ModifyConstant(method = ADD5, constant = @Constant(intValue = 100), require = 2)
    private int lads$historyCap(int vanilla) {
        return ChatFeatures1201.historyLimit(vanilla);
    }

    @Inject(method = ADD5, at = @At("RETURN"), require = 1)
    private void lads$newMessageLaidOut(Component content, MessageSignature signature, int addedTime, GuiMessageTag tag, boolean displayOnly, CallbackInfo ci) {
        if (!displayOnly && ladsLaidOut >= 0) ladsLaidOut = Math.min(ladsLaidOut + 1, allMessages.size());
    }

    /** A resize or deleted message lays out only the lines near the scroll position, not the whole history. */
    @Inject(method = "refreshTrimmedMessage", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$lazyRefresh(CallbackInfo ci) {
        if (!ChatFeatures1201.lazyHistory()) {
            ladsLaidOut = -1;
            return;
        }
        ci.cancel();
        trimmedMessages.clear();
        ladsLaidOut = 0;
        lads$layOut(chatScrollbarPos + getLinesPerPage() + ChatHistory.AHEAD);
    }

    @Inject(method = "scrollChat", at = @At("HEAD"), require = 1)
    private void lads$layOutOlder(int dir, CallbackInfo ci) {
        if (ChatFeatures1201.lazyHistory()) lads$layOut(chatScrollbarPos + dir + getLinesPerPage() + ChatHistory.AHEAD);
    }

    @Unique
    private void lads$layOut(int lines) {
        if (ladsLaidOut < 0) return;
        int width = Mth.floor(getWidth() / getScale());
        ladsLaidOut = ChatHistory.layOut(allMessages, ladsLaidOut, trimmedMessages, lines, message -> true,
            message -> NativeChatHeads1201.split(minecraft.font, message, width));
    }

    /** The newest message starts sliding in as it is added. */
    @Inject(method = ADD3, at = @At("HEAD"), require = 1)
    private void lads$newestMessage(CallbackInfo ci) {
        ladsAnimation.start(minecraft.gui.getGuiTicks());
    }

    /** Nametags renames and Timestamps, written once as the message arrives; then the sender its chat head is for (Chat Heads). */
    @ModifyArg(method = ADD3, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;" + ADD5), index = 0, require = 1)
    private Component lads$message(Component content) {
        Component message = NativeClientTools1201.timestamp(NativeNicknames1201.rename(content));
        NativeChatHeads1201.prepare(message);
        return message;
    }

    @Inject(method = "getWidth(D)I", at = @At("HEAD"), cancellable = true, require = 1)
    private static void lads$chatWidth(double widthOption, CallbackInfoReturnable<Integer> cir) {
        if (Hud1201.enabled("Chat")) cir.setReturnValue((int) Hud1201.number("Chat", "Chat Width", 320));
    }

    @Inject(method = "getHeight(D)I", at = @At("HEAD"), cancellable = true, require = 1)
    private static void lads$chatHeight(double heightOption, CallbackInfoReturnable<Integer> cir) {
        if (Hud1201.enabled("Chat")) cir.setReturnValue((int) Hud1201.number("Chat", "Chat Height", 180));
    }

    @Inject(method = "render", at = @At("HEAD"), require = 1)
    private void lads$beginChat(CallbackInfo ci) {
        // Chat Width moved with the slider (or the module was switched): lines are wrapped again, as vanilla's own width option does.
        int key = Hud1201.enabled("Chat") ? (int) Hud1201.number("Chat", "Chat Width", 320) : -1;
        if (ladsWidthKey != Integer.MIN_VALUE && ladsWidthKey != key) rescaleChat();
        ladsWidthKey = key;
        ladsAnimating = Hud1201.enabled("Chat") && Hud1201.bool("Chat", "Message Animations", true) && ladsAnimation.running();
        ladsProgress = 1f;
        ladsLine = null;
    }

    /** The line the loop is on: its age keys the slide-in. */
    @ModifyVariable(method = "render", at = @At("STORE"), ordinal = 0, require = 1)
    private GuiMessage.Line lads$currentLine(GuiMessage.Line line) {
        ladsLine = line;
        return line;
    }

    /**
     * The line's fade factor, render's fourth double (after the chat opacity, the background opacity and the line spacing): only the
     * newest message fades in, and its background, text and sign indicator fade with it.
     */
    @ModifyVariable(method = "render", at = @At("STORE"), ordinal = 3, require = 1)
    private double lads$fade(double factor) {
        ladsProgress = ladsAnimating && ladsLine != null ? ladsAnimation.progress(ladsLine.addedTime()) : 1f;
        return ladsProgress >= 1f ? factor : factor * ChatAnimation.fade(ladsProgress);
    }

    /** The slide, inside the line's own pose (pushed just before its background); nothing else on the HUD moves. */
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", ordinal = 1, shift = At.Shift.AFTER), require = 1)
    private void lads$slide(GuiGraphics graphics, int tick, int mouseX, int mouseY, CallbackInfo ci) {
        if (ladsProgress < 1f) graphics.pose().translate(ChatAnimation.slide(ladsProgress), 0f, 0f);
    }

    /** Second option read in the render body is textBackgroundOpacity; zero hides only the black line boxes. */
    @ModifyExpressionValue(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;", ordinal = 1), require = 1)
    private Object lads$chatBackground(Object opacity) {
        return Hud1201.enabled("Chat") && !Hud1201.bool("Chat", "Chat Background", true) ? (Object) 0.0D : opacity;
    }

    /** The queued-messages text ("chat.queue") with the Chat module's Text Shadow; chat lines are handled by ChatHeadsComponentMixin. */
    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = DRAW_COMPONENT), require = 1)
    private int lads$queueText(GuiGraphics graphics, Font font, Component text, int x, int y, int color, Operation<Integer> original) {
        return ChatHistory.shadow() ? original.call(graphics, font, text, x, y, color) : graphics.drawString(font, text, x, y, color, false);
    }

    /** Raised: chat lays out above a shorter screen, so it is drawn, hovered and clicked that much higher. */
    @ModifyExpressionValue(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;guiHeight()I"), require = 1)
    private int lads$raiseChat(int height) {
        return height - Raised1201.chat();
    }

    @ModifyExpressionValue(method = {"screenToChatY", "handleChatQueueClicked"}, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;getGuiScaledHeight()I"), require = 2)
    private int lads$raiseChatPointer(int height) {
        return height - Raised1201.chat();
    }
}
