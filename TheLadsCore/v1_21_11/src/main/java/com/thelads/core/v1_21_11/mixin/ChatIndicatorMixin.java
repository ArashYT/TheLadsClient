package com.thelads.core.v1_21_11.mixin;

import com.thelads.core.v1_21_11.feature.NativeQualityOfLife;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Chat "Hide Signing Indicators": hides the line presentation only. Parent message signatures and report data are retained. */
@Mixin(GuiMessage.Line.class)
public class ChatIndicatorMixin {
    @Inject(method = "tag", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$hideIndicator(CallbackInfoReturnable<GuiMessageTag> callback) {
        if (NativeQualityOfLife.enabled("Chat") && NativeQualityOfLife.bool("Chat", "Hide Signing Indicators", true)) callback.setReturnValue(null);
    }
}
