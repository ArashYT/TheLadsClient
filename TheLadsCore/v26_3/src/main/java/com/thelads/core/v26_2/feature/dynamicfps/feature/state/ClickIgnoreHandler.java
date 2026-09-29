// Derived from Dynamic FPS 3.11.9 (MIT); see licenses/DynamicFPS-LICENSE.txt.
package com.thelads.core.v26_2.feature.dynamicfps.feature.state;

import com.thelads.core.v26_2.feature.dynamicfps.config.DynamicFPSConfig;
import com.thelads.core.v26_2.feature.dynamicfps.config.option.IgnoreInitialClick;
import net.minecraft.client.Minecraft;

import java.time.Instant;

public class ClickIgnoreHandler {
	private final long address;
	private static long focusedAt;

    public ClickIgnoreHandler(long address) { this.address = address; }
    public static void onFocus() { focusedAt = Instant.now().toEpochMilli(); }

	public static boolean isFeatureActive() {
		return DynamicFPSConfig.INSTANCE.ignoreInitialClick() != IgnoreInitialClick.DISABLED;
	}

	public static boolean shouldIgnoreClick() {
		Minecraft minecraft = Minecraft.getInstance();
		IgnoreInitialClick config = DynamicFPSConfig.INSTANCE.ignoreInitialClick();

		if (com.thelads.core.v26_2.feature.dynamicfps.DynamicFPSMod.isDisabled() || config == IgnoreInitialClick.DISABLED) {
			return false;
		}

		if (config == IgnoreInitialClick.IN_WORLD && minecraft.gui.screen() != null) {
			return false;
		}

		return focusedAt + 10 >= Instant.now().toEpochMilli();
	}

}
