// Derived from Dynamic FPS 3.11.9 (MIT); see licenses/DynamicFPS-LICENSE.txt.
package com.thelads.core.v1_20_1.perf.dynamicfps.mixin;

import com.thelads.core.v1_20_1.perf.dynamicfps.DynamicFPSMod;
import com.thelads.core.v1_20_1.perf.dynamicfps.PowerState;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DebugScreenOverlay.class)
public class DebugOverlayMixin {
	/** Adds the target rate and the non-default power state to the FPS line of the F3 screen. */
	@Inject(method = "getGameInformation", at = @At("RETURN"), require = 1)
	private void lads$fpsLine(CallbackInfoReturnable<List<String>> callbackInfo) {
		PowerState state = DynamicFPSMod.powerState();

		if (state == PowerState.FOCUSED || DynamicFPSMod.isDisabled()) {
			return;
		}

		List<String> lines = callbackInfo.getReturnValue();

		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);

			if (!line.contains(" fps T: ")) {
				continue;
			}

			int target = DynamicFPSMod.targetFrameRate();
			StringBuilder extra = new StringBuilder("§c (");

			// When running below the actual target frame rate
			// Add rT (real Target) with the actual frame rate
			if (target < 15) {
				extra.append("rT: ").append(target).append(" ");
			}

			extra.append("P: ").append(state.toString().toLowerCase(Locale.ROOT)).append(")§r");
			lines.set(i, line + extra);
			return;
		}
	}
}
