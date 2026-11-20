package com.thelads.core.v26_2.ladsapi.ui.mixin;

import com.thelads.core.v26_2.ladsapi.ui.LadsApiUi26;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lads API lock: a locked input reads as not pressed. Only removes input; Esc, chat and the rest are never locked. */
@Mixin(KeyMapping.class)
public abstract class LockKeyMixin {
    @Inject(method = "isDown()Z", at = @At("HEAD"), cancellable = true)
    private void lads$lockDown(CallbackInfoReturnable<Boolean> cir) {
        if (LadsApiUi26.locksKey((KeyMapping) (Object) this)) cir.setReturnValue(false);
    }

    @Inject(method = "consumeClick()Z", at = @At("HEAD"), cancellable = true)
    private void lads$lockClick(CallbackInfoReturnable<Boolean> cir) {
        if (LadsApiUi26.locksKey((KeyMapping) (Object) this)) cir.setReturnValue(false);
    }
}
