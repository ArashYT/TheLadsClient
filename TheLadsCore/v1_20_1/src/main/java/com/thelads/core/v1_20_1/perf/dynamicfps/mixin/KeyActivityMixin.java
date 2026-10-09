// Derived from Dynamic FPS 3.11.9 (MIT); see licenses/DynamicFPS-LICENSE.txt.
package com.thelads.core.v1_20_1.perf.dynamicfps.mixin;

import com.thelads.core.v1_20_1.perf.dynamicfps.feature.state.IdleHandler;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class KeyActivityMixin {
	@Inject(method = "keyPress", at = @At("HEAD"), require = 1)
	private void lads$key(long window, int key, int scanCode, int action, int modifiers, CallbackInfo callbackInfo) {
		IdleHandler.onActivity();
	}
}
