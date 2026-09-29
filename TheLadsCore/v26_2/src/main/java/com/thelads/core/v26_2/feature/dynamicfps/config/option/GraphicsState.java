// Derived from Dynamic FPS 3.11.9 (MIT); see licenses/DynamicFPS-LICENSE.txt.
package com.thelads.core.v26_2.feature.dynamicfps.config.option;

/**
 * Graphics settings to apply within a given power state.
 */
public enum GraphicsState {
	/**
	 * User-defined graphics settings via the options menu.
	 */
	DEFAULT,

	/**
	 * Reduce graphics settings which do not cause the world to reload.
	 */
	REDUCED,

	/**
	 * Reduce graphics settings to minimal values, this will reload the world!
	 */
	MINIMAL;
}
