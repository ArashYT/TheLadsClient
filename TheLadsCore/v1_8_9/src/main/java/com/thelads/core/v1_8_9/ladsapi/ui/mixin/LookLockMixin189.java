package com.thelads.core.v1_8_9.ladsapi.ui.mixin;

import com.thelads.core.v1_8_9.ladsapi.ui.LadsApiUi189;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lads API lock: the player's own view turn by the mouse is dropped while look is locked (it only removes input). */
@Mixin(Entity.class)
public abstract class LookLockMixin189 {
    @Inject(method = "setAngles", at = @At("HEAD"), cancellable = true)
    private void lads$lockLook(float yaw, float pitch, CallbackInfo ci) {
        if (LadsApiUi189.locksLook((Entity) (Object) this)) ci.cancel();
    }
}
