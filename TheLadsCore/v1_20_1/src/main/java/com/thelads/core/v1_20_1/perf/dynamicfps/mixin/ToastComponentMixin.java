// Derived from Dynamic FPS 3.11.9 (MIT); see licenses/DynamicFPS-LICENSE.txt.
package com.thelads.core.v1_20_1.perf.dynamicfps.mixin;

import com.thelads.core.v1_20_1.perf.dynamicfps.DynamicFPSMod;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ToastComponent.class)
public class ToastComponentMixin {
	/** No free slot means queued toasts wait (they are shown once the state allows toasts again). */
	@Inject(method = "freeSlots", at = @At("HEAD"), cancellable = true, require = 1)
	private void lads$freeSlots(CallbackInfoReturnable<Integer> callbackInfo) {
		if (!DynamicFPSMod.shouldShowToasts()) {
			callbackInfo.setReturnValue(0);
		}
	}
}
