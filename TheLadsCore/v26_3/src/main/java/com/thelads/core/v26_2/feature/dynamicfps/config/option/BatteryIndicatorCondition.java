// Derived from Dynamic FPS 3.11.9 (MIT); see licenses/DynamicFPS-LICENSE.txt.
package com.thelads.core.v26_2.feature.dynamicfps.config.option;

import com.thelads.core.v26_2.feature.dynamicfps.config.DynamicFPSConfig;
import com.thelads.core.v26_2.feature.dynamicfps.feature.battery.BatteryTracker;
import com.thelads.core.v26_2.feature.dynamicfps.feature.battery.BatteryState;

/**
 * Condition under which the battery indicator HUD is shown.
 */
public enum BatteryIndicatorCondition {
	/**
	 * Never show the battery indicator.
	 */
	DISABLED((() -> false)),

	/**
	 * Show battery indicator when the battery is being drained.
	 */
	DRAINING(() -> BatteryTracker.status() == BatteryState.DISCHARGING),

	/**
	 * Show battery indicator when the battery is at a critical level.
	 */
	CRITICAL(() -> {
		int critical = DynamicFPSConfig.INSTANCE.batteryTracker().criticalLevel();
		return DRAINING.isConditionMet() && BatteryTracker.charge() <= critical;
	}),

	/**
	 * Show battery indicator at all times.
	 */
	CONSTANT(() -> true);

	private final Condition condition;

	BatteryIndicatorCondition(Condition condition) {
		this.condition = condition;
	}

	public boolean isConditionMet() {
		return this.condition.isMet();
	}

	@FunctionalInterface
	private interface Condition {
		boolean isMet();
	}
}
