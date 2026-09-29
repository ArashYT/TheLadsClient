// Derived from Dynamic FPS 3.11.9 (MIT); see licenses/DynamicFPS-LICENSE.txt.
package com.thelads.core.v26_2.feature.dynamicfps.feature.volume;

import com.thelads.core.v26_2.feature.dynamicfps.DynamicFPSMod;
import com.thelads.core.v26_2.feature.dynamicfps.config.DynamicFPSConfig;
import com.thelads.core.v26_2.feature.dynamicfps.config.VolumeTransitionConfig;
import com.thelads.core.v26_2.feature.dynamicfps.service.Platform;
import com.thelads.core.v26_2.feature.dynamicfps.util.duck.DuckSoundEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;

import java.util.HashMap;
import java.util.Map;

public class SmoothVolumeHandler {
	private static boolean active = false;
	private static boolean needsUpdating = false;

	private static final Map<SoundSource, Float> currentOverrides = new HashMap<>();

	public static void init() {
		if (active || !DynamicFPSConfig.INSTANCE.volumeTransitionSpeed().isActive()) {
			return;
		}

		active = true;
		Platform.getInstance().registerStartTickEvent(SmoothVolumeHandler::tickVolumes);
	}

	public static void onStateChange() {
		if (active) {
			needsUpdating = true;
		} else {
			for (SoundSource source : SoundSource.values()) {
				updateVolume(source);
			}
		}
	}

	public static float volumeMultiplier(SoundSource source) {
		if (!active) {
			return DynamicFPSMod.volumeMultiplier(source);
		} else {
			return currentOverrides.getOrDefault(source, 1.0f);
		}
	}

	private static void tickVolumes() {
		if (!needsUpdating) {
			return;
		}

		boolean didUpdate = false;
		VolumeTransitionConfig config = DynamicFPSConfig.INSTANCE.volumeTransitionSpeed();

		for (SoundSource source : SoundSource.values()) {
			float desired = DynamicFPSMod.volumeMultiplier(source);
			float current = currentOverrides.getOrDefault(source, 1.0f);

			if (current != desired) {
				didUpdate = true;

				if (current < desired) {
					currentOverrides.put(source, Math.min(desired, current + config.getUp() / 20.0f));
				} else {
					currentOverrides.put(source, Math.max(desired, current - config.getDown() / 20.0f));
				}

				updateVolume(source);
			}
		}

		if (!didUpdate) {
			needsUpdating = false;
		}
	}

	private static void updateVolume(SoundSource source) {
		// Update volume of currently playing sounds
		Minecraft minecraft = Minecraft.getInstance();
		((DuckSoundEngine) ((com.thelads.core.v26_2.feature.dynamicfps.mixin.SoundManagerAccessor) minecraft.getSoundManager())
			.lads$backgroundSoundEngine()).dynamic_fps$updateVolume(source);
	}
}
