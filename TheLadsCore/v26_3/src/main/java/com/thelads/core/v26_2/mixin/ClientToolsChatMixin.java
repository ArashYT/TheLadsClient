package com.thelads.core.v26_2.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.thelads.core.client.ChatAnimation;
import com.thelads.core.client.ChatServerHistory;
import com.thelads.core.client.ChatTweaks;
import com.thelads.core.v26_2.feature.ChatTweaks26;
import com.thelads.core.v26_2.feature.NativeClientTools;
import com.thelads.core.v26_2.feature.NativeNicknames;
import com.thelads.core.v26_2.feature.NativeQualityOfLife;
import com.thelads.core.v26_2.feature.survivaltweaks.NativeSurvivalTweaks;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import com.thelads.core.client.ChatHistory;
import java.util.List;
import java.util.function.Predicate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The Chat module, with the parity-1023 tweaks (ChatTweaks26): Compact Chat, tab and search filters, mentions and profanity, Translate
 * Chat, Toggle Chat and History per Server. Modifies display only: signed packets, signatures and report logs stay intact.
 */
@Mixin(ChatComponent.class)
public abstract class ClientToolsChatMixin implements ChatTweaks26.Access {
    @Unique private static final String RENDER = "extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V";
    @Unique private static final String LINE = "Lnet/minecraft/client/gui/components/ChatComponent$LineConsumer;accept(Lnet/minecraft/client/multiplayer/chat/GuiMessage$Line;IF)V";
    @Unique private final ChatAnimation ladsAnimation = new ChatAnimation();
    @Unique private ChatComponent.ChatGraphicsAccess ladsGraphics;
    @Unique private float ladsSlide;
    @Shadow @Final private List<GuiMessage> allMessages;
    @Shadow @Final private List<GuiMessage.Line> trimmedMessages;
    @Shadow private int chatScrollbarPos;
    @Shadow private Predicate<GuiMessage> visibleMessageFilter;
    @Shadow @Final private net.minecraft.client.Minecraft minecraft;
    @Shadow private int getWidth() { throw new AssertionError(); }
    @Shadow private double getScale() { throw new AssertionError(); }
    @Shadow public abstract int getLinesPerPage();
    /** Infinite History: allMessages[0, ladsLaidOut) have their lines in trimmedMessages; -1 while every message has (vanilla). */
    @Unique private int ladsLaidOut = -1;
    /** Timestamps: the last message they stamped and the message before; the newest stored message (Compact Chat stacks onto it) and its translation. */
    @Unique private Component ladsStamped, ladsUnstamped;
    @Unique private GuiMessage ladsNewest;
    @Unique private ChatTweaks26.Pending ladsPending;
    @Shadow private void refreshTrimmedMessages() { throw new AssertionError(); }

    @Override public List<GuiMessage> lads$messages() { return allMessages; }

    @Override public void lads$relayout() {
        ladsNewest = null;
        ChatTweaks.breakRepeat();
        refreshTrimmedMessages();
    }

    /** History per Server: the messages are kept for the server they came from before vanilla clears them (disconnect). */
    @Inject(method = "clearMessages", at = @At("HEAD"), require = 1)
    private void ladsKeepHistory(boolean history, CallbackInfo ci) {
        ChatServerHistory.clearing(allMessages);
        ladsNewest = null;
        ChatTweaks.breakRepeat();
    }

    /** Chat Tabs and Chat Search: a message outside the chosen tab or search is stored but not laid out (new ones and refreshes). */
    @Inject(method = "addMessageToDisplayQueue", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsFiltered(GuiMessage message, CallbackInfo ci) {
        if (!ChatTweaks26.shown(message.content())) ci.cancel();
    }

    /** Infinite History (ChatHistory) / Longer Chat History: the 100-message and 100-line caps become the ceiling. */
    @ModifyConstant(method = {"addMessageToDisplayQueue", "addMessageToQueue"}, constant = @Constant(intValue = 100), require = 2)
    private int ladsHistoryCap(int vanilla) {
        if (NativeSurvivalTweaks.isLongerChatHistoryEnabled()) {
            return ChatHistory.infinite() ? ChatHistory.limit(vanilla) : 65536;
        }
        return ChatHistory.limit(vanilla);
    }

    @ModifyConstant(method = "addRecentChat", constant = @Constant(intValue = 100), require = 1)
    private int ladsRecentChatCap(int vanilla) {
        if (NativeSurvivalTweaks.isLongerChatHistoryEnabled()) {
            return 65536;
        }
        return vanilla;
    }

    @Inject(method = "addMessageToQueue", at = @At("TAIL"), require = 1)
    private void ladsNewMessageLaidOut(GuiMessage message, CallbackInfo ci) {
        if (ladsLaidOut >= 0) ladsLaidOut = Math.min(ladsLaidOut + 1, allMessages.size());
    }

    /** A resize or deleted message lays out only the lines near the scroll position, not the whole history. */
    @Inject(method = "refreshTrimmedMessages", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsLazyRefresh(CallbackInfo ci) {
        if (!ChatHistory.infinite()) {
            ladsLaidOut = -1;
            return;
        }
        ci.cancel();
        trimmedMessages.clear();
        ladsLaidOut = 0;
        ladsLayOut(chatScrollbarPos + getLinesPerPage() + ChatHistory.AHEAD);
    }

    @Inject(method = "scrollChat", at = @At("HEAD"), require = 1)
    private void ladsLayOutOlder(int dir, CallbackInfo ci) {
        if (ChatHistory.infinite()) ladsLayOut(chatScrollbarPos + dir + getLinesPerPage() + ChatHistory.AHEAD);
    }

    @Unique
    private void ladsLayOut(int lines) {
        if (ladsLaidOut < 0) return;
        int width = net.minecraft.util.Mth.floor(getWidth() / getScale());
        ladsLaidOut = ChatHistory.layOut(allMessages, ladsLaidOut, trimmedMessages, lines, message -> visibleMessageFilter.test(message) && ChatTweaks26.shown(message.content()), message -> {
            var split = message.splitLines(minecraft.font, width);
            var parts = new java.util.ArrayList<GuiMessage.Line>(split.size());
            for (int i = 0; i < split.size(); i++) parts.add(new GuiMessage.Line(message, split.get(i), i == split.size() - 1));
            return parts;
        });
    }

    @ModifyVariable(method = {"addClientSystemMessage", "addServerSystemMessage", "addPlayerMessage"},
        at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 3)
    private Component ladsTimestamp(Component content) {
        ladsUnstamped = content;
        return ladsStamped = NativeClientTools.timestamp(content);
    }

    /**
     * Nametags renames and the chat tweaks (ChatTweaks26: Compact Chat removes the copy before and counts, mentions, profanity,
     * translation), rewritten once as a message arrives; they work on the message without its timestamp, which goes back on after.
     */
    @ModifyVariable(method = "addMessage", at = @At("HEAD"), argsOnly = true, require = 1)
    private Component ladsRename(Component content) {
        boolean stamped = content == ladsStamped && ladsUnstamped != null;
        Component renamed = NativeNicknames.rename(stamped ? ladsUnstamped : content);
        ladsStamped = ladsUnstamped = null;
        ChatTweaks26.server(this);
        String plain = renamed.getString();
        long now = System.currentTimeMillis();
        int repeat = ChatTweaks.repeat(plain, now);
        if (repeat > 1 && ladsNewest != null && !allMessages.isEmpty() && allMessages.get(0) == ladsNewest) {
            allMessages.remove(0);
            refreshTrimmedMessages();
        } else if (repeat > 1) { // the copy before is gone (deleted, cleared): this one starts a new count
            ChatTweaks.breakRepeat();
            repeat = ChatTweaks.repeat(plain, now);
        }
        ladsPending = ChatTweaks26.translate(plain);
        Component message = ChatTweaks26.incoming(renamed, plain, repeat);
        return stamped ? NativeClientTools.timestamp(message) : message;
    }

    @ModifyArg(method = "addMessage", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;addMessageToDisplayQueue(Lnet/minecraft/client/multiplayer/chat/GuiMessage;)V"), require = 1)
    private GuiMessage ladsNewestMessage(GuiMessage message) {
        ladsAnimation.start(message);
        ladsNewest = message;
        if (ladsPending != null) ladsPending.message = message;
        ladsPending = null;
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

    @Inject(method = RENDER, at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsBeginChat(ChatComponent.ChatGraphicsAccess graphics, int screenHeight, int ticks, ChatComponent.DisplayMode mode, CallbackInfo ci) {
        if (ChatTweaks.hidden && !mode.foreground) { // Toggle Chat: hidden unless typing in it or peeking (ChatTweaksHudMixin26)
            ci.cancel();
            return;
        }
        com.thelads.core.v26_2.feature.NativeQualityOfLife.chatWithoutShadow = !ChatHistory.shadow();
        boolean animate = NativeQualityOfLife.enabled("Chat") && NativeQualityOfLife.bool("Chat", "Message Animations", true);
        ladsGraphics = animate && ladsAnimation.running() ? graphics : null;
    }

    @Inject(method = RENDER, at = @At("RETURN"), require = 1)
    private void ladsEndChat(CallbackInfo ci) { ladsGraphics = null; com.thelads.core.v26_2.feature.NativeQualityOfLife.chatWithoutShadow = false; }

    /** Raised, and Above Armor Bar: chat lays out above a shorter screen, so it is drawn, hovered and clicked that much higher. */
    @ModifyVariable(method = RENDER, at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 1)
    private int ladsRaiseChat(int screenHeight) { return screenHeight - com.thelads.core.v26_2.feature.Raised26.chatLift(); }

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
