package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.ConnectionTweaks;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hide the line presentation only. Parent message signatures and report data are retained. */
@Mixin(GuiMessage.Line.class)
public class ChatIndicatorMixin {
    @Inject(method = "tag", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$hideIndicator(CallbackInfoReturnable<GuiMessageTag> callback) {
        if (ConnectionTweaks.hideChatSigning()) callback.setReturnValue(null);
    }
}
