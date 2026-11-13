package com.thelads.smp.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.sdl.SDLKeyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * isKeyDown indexes SDL's keyboard-state array with the key's value. A mod key saved as an SDL keycode instead of a scancode
 * (Fabrishot's screenshot key: 1073741890) threw IndexOutOfBoundsException from KeyMapping.setAll and crashed every world join.
 * Out-of-range keys now read as not pressed.
 */
@Mixin(InputConstants.class)
public abstract class MixinInputConstantsKeyBounds {
    @Inject(method = "isKeyDown(I)Z", at = @At("HEAD"), cancellable = true)
    private static void ladssmp$boundsCheck(int key, CallbackInfoReturnable<Boolean> cir) {
        java.nio.ByteBuffer state = SDLKeyboard.SDL_GetKeyboardState();
        if (state != null && (key < 0 || key >= state.limit())) cir.setReturnValue(false);
    }
}
