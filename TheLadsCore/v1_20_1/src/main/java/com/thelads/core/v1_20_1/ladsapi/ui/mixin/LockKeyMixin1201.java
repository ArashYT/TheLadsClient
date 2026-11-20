package com.thelads.core.v1_20_1.ladsapi.ui.mixin;

import com.thelads.core.v1_20_1.ladsapi.ui.LadsApiUi1201;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lads API lock: a locked input reads as not pressed. Only removes input; Esc, chat and the rest are never locked. */
@Mixin(KeyMapping.class)
public abstract class LockKeyMixin1201 {
    @Inject(method = "isDown()Z", at = @At("HEAD"), cancellable = true)
    private void lads$lockDown(CallbackInfoReturnable<Boolean> cir) {
        if (LadsApiUi1201.locksKey((KeyMapping) (Object) this)) cir.setReturnValue(false);
    }

    /** At the return: the queued click is used up first, so it does not fire the moment the lock ends. */
    @Inject(method = "consumeClick()Z", at = @At("RETURN"), cancellable = true)
    private void lads$lockClick(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && LadsApiUi1201.locksKey((KeyMapping) (Object) this)) cir.setReturnValue(false);
    }
}
