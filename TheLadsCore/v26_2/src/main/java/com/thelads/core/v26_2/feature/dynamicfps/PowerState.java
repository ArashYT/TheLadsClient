// Derived from Dynamic FPS 3.11.9 (MIT); see licenses/DynamicFPS-LICENSE.txt.
package com.thelads.core.v26_2.feature.dynamicfps;

/**
 * An analog for device power states, applied to the Minecraft window.
 *
 * Power states are prioritized based on their order here, see DynamicFPSMod.checkForStateChanges for impl details.
 */
public enum PowerState {
	/**
	 * Window is currently focused.
	 */
	FOCUSED(ConfigurabilityLevel.NONE),

	/**
	 * Mouse positioned over unfocused window.
	 */
	HOVERED(ConfigurabilityLevel.FULL),

	/**
	 * Another application is focused.
	 */
	UNFOCUSED(ConfigurabilityLevel.FULL),

	/**
	 * Window minimized or otherwise hidden.
	 */
	INVISIBLE(ConfigurabilityLevel.FULL),

	/**
	 * The device is currently on battery.
	 */
	UNPLUGGED(ConfigurabilityLevel.SOME),

	/**
	 * User hasn't sent input for some time.
	 */
	ABANDONED(ConfigurabilityLevel.FULL);

	public final ConfigurabilityLevel configurabilityLevel;

	PowerState(ConfigurabilityLevel configurabilityLevel) {
		this.configurabilityLevel = configurabilityLevel;
	}

	public enum ConfigurabilityLevel {
		NONE,
		SOME,
		FULL;
	}
}
