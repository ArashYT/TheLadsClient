// Derived from Dynamic FPS 3.11.9 (MIT); see licenses/DynamicFPS-LICENSE.txt.
package com.thelads.core.v26_2.feature.dynamicfps.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.thelads.core.v26_2.feature.dynamicfps.DynamicFPSMod;
import com.thelads.core.v26_2.feature.dynamicfps.config.option.GraphicsState;
import com.thelads.core.v26_2.feature.dynamicfps.feature.state.OptionHolder;
import com.thelads.core.v26_2.feature.dynamicfps.feature.volume.SmoothVolumeHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Options.class)
public abstract class OptionsMixin {
	// Reset runtime modified graphics settings to the user-specified defaults during saving.
	// Prevents the game from saving the reduced or minimal preset to disk and loading it again.
	@com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "save")
	private void lads$saveUserGraphics(com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
		GraphicsState state = DynamicFPSMod.graphicsState();
		var options = Minecraft.getInstance().options;
		boolean qaPause = com.thelads.core.v26_2.feature.NativeWorldVerification.active();
		boolean pauseBefore = options.pauseOnLostFocus;
		if (qaPause) options.pauseOnLostFocus = com.thelads.core.v26_2.feature.NativeWorldVerification.originalPauseOnLostFocus();
		if (state != GraphicsState.DEFAULT) OptionHolder.applyOptions(Minecraft.getInstance().options, GraphicsState.DEFAULT);
		try { original.call(); }
		finally {
			if (qaPause) options.pauseOnLostFocus = pauseBefore;
			if (state != GraphicsState.DEFAULT) OptionHolder.applyOptions(Minecraft.getInstance().options, state);
		}
	}

	/**
	 * Apply the volume multiplier to any newly-played sounds.
	 */
	@ModifyReturnValue(method = "getSoundSourceVolume", at = @At("RETURN"))
	private float getSoundSourceVolume(float value, @Local(argsOnly = true) @Nullable SoundSource source) {
		return value * SmoothVolumeHandler.volumeMultiplier(source);
	}
}
