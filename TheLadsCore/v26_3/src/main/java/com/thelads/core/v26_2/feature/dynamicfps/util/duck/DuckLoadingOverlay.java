// Derived from Dynamic FPS 3.11.9 (MIT); see licenses/DynamicFPS-LICENSE.txt.
package com.thelads.core.v26_2.feature.dynamicfps.util.duck;

public interface DuckLoadingOverlay {
	public default boolean dynamic_fps$isReloadComplete() {
		throw new RuntimeException("No implementation for dynamic_fps$isReloadComplete was found.");
	}
}
