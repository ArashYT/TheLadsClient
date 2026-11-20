package com.thelads.core.v1_8_9.ladsapi.ui.mixin;

import com.thelads.core.v1_8_9.ladsapi.ui.LadsApiUi189;
import com.thelads.core.v1_8_9.ladsapi.ui.ServerKeys189;
import java.util.List;
import net.minecraft.client.settings.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lads API lock: a locked key reads as not pressed (it only removes input; Esc, chat and the rest are never mapped). A number key
 * that answers an open vote is used up here. A server action key can leave the game's key list again through lads$unregister.
 */
@Mixin(KeyBinding.class)
public abstract class KeyBindingLockMixin189 implements ServerKeys189.Removable {
    @Shadow private static List<KeyBinding> keybindArray;

    @Inject(method = "isKeyDown", at = @At("HEAD"), cancellable = true)
    private void lads$lockDown(CallbackInfoReturnable<Boolean> cir) {
        if (LadsApiUi189.locksKey((KeyBinding) (Object) this)) cir.setReturnValue(false);
    }

    /** At the return: the queued press is used up first, so it does not fire the moment the lock ends. */
    @Inject(method = "isPressed", at = @At("RETURN"), cancellable = true)
    private void lads$lockPress(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && LadsApiUi189.eatsPress((KeyBinding) (Object) this)) cir.setReturnValue(false);
    }

    @Override
    public void lads$unregister() {
        keybindArray.remove(this);
    }
}
