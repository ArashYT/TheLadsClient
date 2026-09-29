// Derived from Dynamic FPS 3.11.9 (MIT); see licenses/DynamicFPS-LICENSE.txt.
package com.thelads.core.v26_2.feature.dynamicfps.util.duck;

import net.minecraft.sounds.SoundSource;

public interface DuckSoundEngine {
	default void dynamic_fps$updateVolume(SoundSource source) {
		throw new RuntimeException("No implementation for dynamic_fps$updateVolume was found.");
	}
}
