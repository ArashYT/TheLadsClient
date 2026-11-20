package com.thelads.core.v1_8_9.ladsapi.ui.mixin;

import com.thelads.core.v1_8_9.ladsapi.ui.LadsApiUi189;
import net.minecraft.entity.player.InventoryPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lads API lock: the mouse wheel does not change the hotbar slot while the hotbar is locked (only the wheel calls this). */
@Mixin(InventoryPlayer.class)
public abstract class ScrollLockMixin189 {
    @Inject(method = "changeCurrentItem", at = @At("HEAD"), cancellable = true)
    private void lads$lockScroll(int wheel, CallbackInfo ci) {
        if (LadsApiUi189.locksScroll((InventoryPlayer) (Object) this)) ci.cancel();
    }
}
