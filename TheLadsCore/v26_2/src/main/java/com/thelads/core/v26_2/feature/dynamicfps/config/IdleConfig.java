// Derived from Dynamic FPS 3.11.9 (MIT); see licenses/DynamicFPS-LICENSE.txt.
package com.thelads.core.v26_2.feature.dynamicfps.config;

import com.thelads.core.v26_2.feature.dynamicfps.config.option.IdleCondition;

public class IdleConfig {
	private int timeout;
	private IdleCondition condition;

	public int timeout() {
		return this.timeout;
	}

	public void setTimeout(int value) {
		this.timeout = value;
	}

	public IdleCondition condition() {
		return this.condition;
	}

	public void setCondition(IdleCondition value) {
		this.condition = value;
	}
}
