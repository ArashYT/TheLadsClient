// Derived from Dynamic FPS 3.11.9 (MIT); see licenses/DynamicFPS-LICENSE.txt.
package com.thelads.core.v1_20_1.perf.dynamicfps.mixin;

import com.thelads.core.v1_20_1.perf.dynamicfps.feature.state.ClickIgnoreHandler;
import com.thelads.core.v1_20_1.perf.dynamicfps.feature.state.IdleHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mouse input wakes the idle state; the click that focuses the window can be swallowed (Ignore Initial Click). */
@Mixin(MouseHandler.class)
public abstract class InputActivityMixin {
	@Inject(method = "onPress", at = @At("HEAD"), cancellable = true, require = 1)
	private void lads$press(long window, int button, int action, int modifiers, CallbackInfo callbackInfo) {
		if (window != Minecraft.getInstance().getWindow().getWindow()) {
			return;
		}

		IdleHandler.onActivity();

		if (ClickIgnoreHandler.shouldIgnoreClick()) {
			callbackInfo.cancel();
		}
	}

	@Inject(method = "onMove", at = @At("HEAD"), require = 1)
	private void lads$move(long window, double x, double y, CallbackInfo callbackInfo) {
		IdleHandler.onActivity();
	}

	@Inject(method = "onScroll", at = @At("HEAD"), require = 1)
	private void lads$scroll(long window, double horizontal, double vertical, CallbackInfo callbackInfo) {
		IdleHandler.onActivity();
	}
}
