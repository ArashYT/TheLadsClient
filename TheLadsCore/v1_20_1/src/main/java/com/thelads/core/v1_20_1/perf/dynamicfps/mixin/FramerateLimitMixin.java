// Derived from Dynamic FPS 3.11.9 (MIT); see licenses/DynamicFPS-LICENSE.txt.
package com.thelads.core.v1_20_1.perf.dynamicfps.mixin;

import com.mojang.blaze3d.platform.Window;
import com.thelads.core.v1_20_1.perf.dynamicfps.Constants;
import com.thelads.core.v1_20_1.perf.dynamicfps.DynamicFPSMod;
import com.thelads.core.v1_20_1.perf.dynamicfps.PowerState;
import com.thelads.core.v1_20_1.perf.dynamicfps.config.DynamicFPSConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The frame limit the render loop sleeps to (Minecraft.getFramerateLimit): the power state's target, never below 15. */
@Mixin(Minecraft.class)
public abstract class FramerateLimitMixin {
	@Shadow
	@Final
	private Window window;

	@Shadow
	@Final
	public Options options;

	@Shadow
	public net.minecraft.client.multiplayer.ClientLevel level;

	@Shadow
	public net.minecraft.client.gui.screens.Screen screen;

	@Shadow
	private net.minecraft.client.gui.screens.Overlay overlay;

	@Inject(method = "getFramerateLimit", at = @At("HEAD"), cancellable = true, require = 1)
	private void lads$framerateLimit(CallbackInfoReturnable<Integer> callbackInfo) {
		if (DynamicFPSMod.isDisabled()) {
			return; // vanilla
		}

		PowerState state = DynamicFPSMod.powerState();
		int configured = this.window.getFramerateLimit();

		if (state != PowerState.FOCUSED) {
			// Instruct Minecraft to render a minimum of 15 FPS
			// Going lower here makes resuming again feel sluggish
			callbackInfo.setReturnValue(Math.max(DynamicFPSMod.targetFrameRate(), Constants.MIN_FRAME_RATE_LIMIT));
		} else if (this.lads$isInLevel()) {
			callbackInfo.setReturnValue(configured);
		} else if (DynamicFPSConfig.INSTANCE.uncapMenuFrameRate()) {
			callbackInfo.setReturnValue(this.lads$menuFramerateLimit(configured));
		} else {
			callbackInfo.setReturnValue(Constants.TITLE_FRAME_RATE_LIMIT);
		}
	}

	@Unique
	private int lads$menuFramerateLimit(int configured) {
		if (this.options.enableVsync().get()) {
			// VSync will regulate to a non-infinite value
			return Constants.NO_FRAME_RATE_LIMIT;
		} else {
			// Even though the option "uncaps" the frame rate the limit is 250 FPS.
			// Since otherwise this will just cause coil whine with no real benefit
			return Math.min(configured, Constants.NO_FRAME_RATE_LIMIT - 10);
		}
	}

	@Unique
	private boolean lads$isInLevel() {
		return this.level != null || this.screen == null && this.overlay == null;
	}
}
