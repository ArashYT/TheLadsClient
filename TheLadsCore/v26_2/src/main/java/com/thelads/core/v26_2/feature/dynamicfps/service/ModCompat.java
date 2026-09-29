// Derived from Dynamic FPS 3.11.9 (MIT); see licenses/DynamicFPS-LICENSE.txt.
package com.thelads.core.v26_2.feature.dynamicfps.service;

public interface ModCompat {
	static void initialize() { ((LadsModCompat) Services.MOD_COMPAT).initialize(); }
	boolean isDisabled();

	boolean disableOverlayOptimization();

	static ModCompat getInstance() {
		return Services.MOD_COMPAT;
	}
}
