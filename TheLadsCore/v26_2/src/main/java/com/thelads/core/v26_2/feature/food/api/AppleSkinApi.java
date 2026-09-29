// Adapted from AppleSkin 3.0.10 (Unlicense), commit 62513191f6a3497447595c2215d466ad1d2bdb92.
// Built into The Lads Core; see assets/theladscore/licenses/AppleSkin-Unlicense.txt.
package com.thelads.core.v26_2.feature.food.api;

/**
 * Used as an entrypoint in order to allow for integration with AppleSkin
 * without depending on AppleSkin at runtime.
 */
public interface AppleSkinApi
{
	/**
	 * Called at client-init in order for the implementer to register events with
	 * the AppleSkin API ({@see com.thelads.core.v26_2.feature.food.api.event})
	 */
	void registerEvents();
}
