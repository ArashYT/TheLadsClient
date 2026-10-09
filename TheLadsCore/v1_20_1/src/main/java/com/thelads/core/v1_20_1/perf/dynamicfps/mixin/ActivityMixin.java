// Derived from Dynamic FPS 3.11.9 (MIT); see licenses/DynamicFPS-LICENSE.txt.
package com.thelads.core.v1_20_1.perf.dynamicfps.mixin;

import com.thelads.core.v1_20_1.perf.dynamicfps.feature.state.ClickIgnoreHandler;
import com.thelads.core.v1_20_1.perf.dynamicfps.feature.state.IdleHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Opening or closing a screen counts as activity for the idle timer. */
@Mixin(Minecraft.class)
public abstract class ActivityMixin {
	@Inject(method = "setScreen", at = @At("HEAD"), require = 1)
	private void lads$setScreen(Screen screen, CallbackInfo callbackInfo) {
		IdleHandler.onActivity();
	}
}
