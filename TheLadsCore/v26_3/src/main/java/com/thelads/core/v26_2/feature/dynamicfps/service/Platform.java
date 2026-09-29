// Derived from Dynamic FPS 3.11.9 (MIT); see licenses/DynamicFPS-LICENSE.txt.
package com.thelads.core.v26_2.feature.dynamicfps.service;

import com.thelads.core.v26_2.feature.dynamicfps.util.Version;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;

public interface Platform {
	String getName();

	Path getCacheDir();
	Path getConfigDir();
	boolean isDevelopmentEnvironment();

	boolean isModLoaded(String modId);
	Optional<Version> getModVersion(String modId);

	void registerStartTickEvent(StartTickEvent event);

	default boolean isModLoaded(String ...modId) {
		return Arrays.stream(modId).anyMatch(this::isModLoaded);
	}

	@FunctionalInterface
	interface StartTickEvent {
		void onStartTick();
	}

	static Platform getInstance() {
		return Services.PLATFORM;
	}
}
