// Derived from Dynamic FPS 3.11.9 (MIT); see licenses/DynamicFPS-LICENSE.txt.
package com.thelads.core.v1_20_1.perf.dynamicfps.mixin;

import com.thelads.core.v1_20_1.perf.dynamicfps.DynamicFPSMod;
import com.thelads.core.v1_20_1.perf.dynamicfps.config.option.GraphicsState;
import com.thelads.core.v1_20_1.perf.dynamicfps.feature.state.OptionHolder;
import com.thelads.core.v1_20_1.perf.dynamicfps.feature.volume.SmoothVolumeHandler;
import net.minecraft.client.Options;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Options.class)
public abstract class OptionsMixin {
	@Unique
	private GraphicsState lads$savedState = GraphicsState.DEFAULT;

	// Reset runtime modified graphics settings to the user-specified defaults during saving.
	// Prevents the game from saving the reduced or minimal preset to disk and loading it again.
	@Inject(method = "save", at = @At("HEAD"), require = 1)
	private void lads$restoreUserGraphicsBeforeSave(CallbackInfo callbackInfo) {
		GraphicsState state = DynamicFPSMod.graphicsState();
		this.lads$savedState = state;

		if (state != GraphicsState.DEFAULT) {
			OptionHolder.applyOptions((Options) (Object) this, GraphicsState.DEFAULT);
		}
	}

	@Inject(method = "save", at = @At("RETURN"), require = 1)
	private void lads$reapplyGraphicsAfterSave(CallbackInfo callbackInfo) {
		if (this.lads$savedState != GraphicsState.DEFAULT) {
			OptionHolder.applyOptions((Options) (Object) this, this.lads$savedState);
			this.lads$savedState = GraphicsState.DEFAULT;
		}
	}

	/**
	 * Apply the volume multiplier to the master gain and to every newly-played sound.
	 */
	@Inject(method = "getSoundSourceVolume", at = @At("RETURN"), cancellable = true, require = 1)
	private void lads$soundSourceVolume(SoundSource source, CallbackInfoReturnable<Float> callbackInfo) {
		callbackInfo.setReturnValue(callbackInfo.getReturnValueF() * SmoothVolumeHandler.volumeMultiplier(source));
	}
}
