package com.thelads.core.v1_20_1.game.mixin;

import com.thelads.core.v1_20_1.game.anim.NativeOldAnimations1201;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Gui.class)
public class OldAnimationsHeartsMixin {
    // 1.7 Animations' No heart flashing: the hearts' blink flag is the last argument of renderHearts (also called by Forge's health overlay).
    @ModifyVariable(method = "renderHearts", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private boolean lads$noHeartFlash(boolean blink) {
        return NativeOldAnimations1201.heartsBlink(blink);
    }
}
