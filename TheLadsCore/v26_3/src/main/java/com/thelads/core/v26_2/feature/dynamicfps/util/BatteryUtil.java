// Derived from Dynamic FPS 3.11.9 (MIT); see licenses/DynamicFPS-LICENSE.txt.
package com.thelads.core.v26_2.feature.dynamicfps.util;

import com.thelads.core.v26_2.feature.dynamicfps.feature.battery.BatteryState;

public class BatteryUtil {
	/**
	 * @return whether the state is charging or full.
	 */
	public static boolean isCharging(BatteryState state) {
		// Some devices seem to like frantically changing from
		// Charging to full, even when the battery is not full
		// Prevents frequent HUD changes and notification spam
		return state == BatteryState.CHARGING || state == BatteryState.FULL;
	}
}
