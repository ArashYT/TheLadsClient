package com.thelads.core.v26_2.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.thelads.core.v26_2.feature.NativeChatHeads;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Chat Heads (NativeChatHeads) in the line pass of ChatComponent's chat drawing, which visits lines top to bottom: a message's
 * first visible line draws its head, and every line of a message with one has its text (and clickable and hovered text) moved
 * right through the chat's own pose, so drawing and clicking stay in step. The signing indicator stays at the edge.
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
        int offset = NativeChatHeads.offset(message);
        if (offset == 0) return original.call(graphics, top, opacity, text);
        if (first) NativeChatHeads.draw(message, top, opacity);
        graphics.updatePose(pose -> pose.translate(offset, 0f));
        try {
            return original.call(graphics, top, opacity, text);
        } finally {
            graphics.updatePose(pose -> pose.translate(-offset, 0f));
        }
    }

    @ModifyExpressionValue(method = "accept", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/chat/GuiMessage$Line;getTagIconLeft(Lnet/minecraft/client/gui/Font;)I"), require = 1)
    private int lads$iconAfterHead(int left, @Local(argsOnly = true) GuiMessage.Line line) {
        return left + NativeChatHeads.offset(line.parent());
    }
}
