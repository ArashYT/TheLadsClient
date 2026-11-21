package com.thelads.core.v26_2.mixin;

import com.thelads.core.perf.ChatAsync;
import com.thelads.core.v26_2.feature.ChatAsync26;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.ComponentRenderUtils;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Async Chat: hands the main thread the wrap the worker already did, when it is for exactly this text, width and font. */
@Mixin(ComponentRenderUtils.class)
public abstract class WrapComponentsMixin26 {
    @Inject(method = "wrapComponents", at = @At("HEAD"), cancellable = true, require = 1)
    private static void lads$prepared(FormattedText text, int width, Font font, CallbackInfoReturnable<List<FormattedCharSequence>> cir) {
        if (ChatAsync.current instanceof ChatAsync26.Prepared prepared && prepared.matches(text, width, font)) {
            ChatAsync.current = null;
            cir.setReturnValue(prepared.lines());
        }
    }
}
