// Derived from Dynamic FPS 3.11.9 (MIT); see licenses/DynamicFPS-LICENSE.txt.
package com.thelads.core.v26_2.feature.dynamicfps.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import com.thelads.core.v26_2.feature.dynamicfps.Constants;
import com.thelads.core.v26_2.feature.dynamicfps.DynamicFPSMod;
import com.thelads.core.v26_2.feature.dynamicfps.PowerState;
import com.thelads.core.v26_2.feature.dynamicfps.config.DynamicFPSConfig;
import com.thelads.core.v26_2.feature.dynamicfps.config.option.IdleCondition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FramerateLimitTracker.class)
public class FramerateLimitTrackerMixin {
	@Inject(method = "onInputReceived", at = @At("HEAD"))
	private void lads$onInput(CallbackInfo callback) {
		com.thelads.core.v26_2.feature.dynamicfps.feature.state.IdleHandler.onActivity();
	}
	@Shadow
	@Final
	private Options options;

	@Shadow
	@Final
	private Minecraft minecraft;

	@Shadow
	private int framerateLimit;

	@Inject(method = "getFramerateLimit", at = @At("HEAD"), cancellable = true)
	private void getFramerateLimit(CallbackInfoReturnable<Integer> callbackInfo) {
		if (DynamicFPSMod.isDisabled()) return;
		PowerState state = DynamicFPSMod.powerState();

		if (state != PowerState.FOCUSED) {
			// Instruct Minecraft to render a minimum of 15 FPS
			// Going lower here makes resuming again feel sluggish
			callbackInfo.setReturnValue(Math.max(this.getFramerateTarget(), Constants.MIN_FRAME_RATE_LIMIT));
		} else {
			IdleCondition condition = DynamicFPSConfig.INSTANCE.idle().condition();

			// Bypass all the vanilla idle checking code
			// Note: If Dynamic FPS thinks the user is idle the power state would be different above
			if (condition != IdleCondition.VANILLA) {
				// Since we're bypassing the idle checking code we also need to set the menu FPS here as it's bundled now
				if (isInLevel()) {
					callbackInfo.setReturnValue(this.framerateLimit);
				} else if(DynamicFPSConfig.INSTANCE.uncapMenuFrameRate()) {
					callbackInfo.setReturnValue(this.getMenuFramerateLimit());
				}else {
					callbackInfo.setReturnValue(Constants.TITLE_FRAME_RATE_LIMIT);
				}
			}
		}
	}

	/**
	 * Conditionally bypasses the main menu frame rate limit while using the vanilla idle code.
	 * <p>
	 * This is done in two cases:
	 * - The window is active, and the user wants to uncap the frame rate
	 * - The window is inactive, and the current FPS limit should be lower
	 */
	@Inject(method = "getFramerateLimit", at = @At(value = "CONSTANT", args = "intValue=60"), cancellable = true)
	private void getMenuFramerateLimit(CallbackInfoReturnable<Integer> callbackInfo) {
		if (DynamicFPSMod.isDisabled()) return;
		int limit = this.getFramerateTarget();

		if (DynamicFPSMod.powerState() != PowerState.FOCUSED) {
			// Vanilla returns 60 here
			// Only overwrite if our current limit is lower
			if (limit < 60) {
				callbackInfo.setReturnValue(limit);
			}
		} else if (DynamicFPSConfig.INSTANCE.uncapMenuFrameRate()) {
			callbackInfo.setReturnValue(this.getMenuFramerateLimit());
		}
	}

	@Unique
	private int getFramerateTarget() {
		return DynamicFPSMod.targetFrameRate();
	}

	@Unique
	private int getMenuFramerateLimit() {
		if (this.options.enableVsync().get()) {
			// VSync will regulate to a non-infinite value
			return Constants.NO_FRAME_RATE_LIMIT;
		} else {
			// Even though the option "uncaps" the frame rate the limit is 250 FPS.
			// Since otherwise this will just cause coil whine with no real benefit
			return Math.min(this.framerateLimit, Constants.NO_FRAME_RATE_LIMIT - 10);
		}
	}

	@Unique
	private boolean isInLevel() {
		return this.minecraft.level != null || this.minecraft.gui.screen() == null && this.minecraft.gui.overlay() == null;
	}
}
