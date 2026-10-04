package com.thelads.core.v26_2.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.thelads.core.v26_2.feature.NativeChatHeads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Chat Heads (NativeChatHeads) in the line pass of ChatComponent's chat drawing, which visits lines top to bottom. Start of line:
 * a message's first visible line draws its head, and every line of a message with one has its text (and clickable and hovered
 * text) moved right through the chat's own pose. Before name: the message's first line is drawn in two parts, the text before
 * the sender's name and the rest, with the head between them and the rest moved right by the head. The signing indicator stays
 * at the edge.
 */
@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent$1")
public abstract class ChatHeadsLineMixin {
    @Unique private GuiMessage lads$above;

    @WrapOperation(method = "accept", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;handleMessage(IFLnet/minecraft/util/FormattedCharSequence;)Z"), require = 1)
    private boolean lads$head(ChatComponent.ChatGraphicsAccess graphics, int top, float opacity, FormattedCharSequence text, Operation<Boolean> original,
                              @Local(argsOnly = true) GuiMessage.Line line) {
        GuiMessage message = line.parent();
        boolean first = message != lads$above;
        lads$above = message;
        int offset = NativeChatHeads.shift(line);
        if (offset == 0) return original.call(graphics, top, opacity, text);
        int at = 0, x = 0;
        boolean hovered = false;
        if (NativeChatHeads.beforeName()) {
            at = NativeChatHeads.at(message, text);
            if (at > 0) {
                FormattedCharSequence before = NativeChatHeads.slice(text, 0, at);
                hovered = original.call(graphics, top, opacity, before);
                x = Minecraft.getInstance().font.width(before);
                text = NativeChatHeads.slice(text, at, Integer.MAX_VALUE);
            }
            NativeChatHeads.draw(message, x, top, opacity);
        } else if (first) NativeChatHeads.draw(message, 0, top, opacity);
        float move = x + offset;
        graphics.updatePose(pose -> pose.translate(move, 0f));
        try {
            return original.call(graphics, top, opacity, text) | hovered;
        } finally {
            graphics.updatePose(pose -> pose.translate(-move, 0f));
        }
    }

    @ModifyExpressionValue(method = "accept", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/chat/GuiMessage$Line;getTagIconLeft(Lnet/minecraft/client/gui/Font;)I"), require = 1)
    private int lads$iconAfterHead(int left, @Local(argsOnly = true) GuiMessage.Line line) {
        return left + NativeChatHeads.shift(line);
    }
}
