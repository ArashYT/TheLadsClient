// Derived from Dynamic FPS 3.11.9 (MIT); see licenses/DynamicFPS-LICENSE.txt.
package com.thelads.core.v26_2.feature.dynamicfps.mixin;

import net.minecraft.client.gui.components.toasts.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.thelads.core.v26_2.feature.dynamicfps.DynamicFPSMod;

@Mixin(ToastManager.class)
public class ToastManagerMixin {
	@Inject(method = "freeSlotCount", at = @At("HEAD"), cancellable = true)
	private void freeSlotCount(CallbackInfoReturnable<Integer> callbackInfo) {
		if (!DynamicFPSMod.shouldShowToasts()) {
			callbackInfo.setReturnValue(0);
		}
	}
}
