// Derived from Dynamic FPS 3.11.9 (MIT); see licenses/DynamicFPS-LICENSE.txt.
package com.thelads.core.v26_2.feature.dynamicfps.util;

import com.thelads.core.v26_2.feature.dynamicfps.DynamicFPSMod;
import com.thelads.core.v26_2.feature.dynamicfps.service.Platform;
import net.minecraft.client.KeyMapping;

import com.mojang.blaze3d.platform.InputConstants;

public final class KeyMappingHandler {
	private final KeyMapping keyMapping;
	private boolean isHoldingKey = false;
	private final PressHandler pressHandler;

	private static final KeyMappingHandler[] KEY_MAPPING_HANDLERS = {
		new KeyMappingHandler(
			Components.translationKey("key", "toggle_forced"),
			KeyMapping.Category.MISC,
			DynamicFPSMod::toggleForceLowFPS
		),
		new KeyMappingHandler(
			Components.translationKey("key", "toggle_disabled"),
			KeyMapping.Category.MISC,
			DynamicFPSMod::toggleDisabled
		)
	};

	private KeyMappingHandler(String translationKey, KeyMapping.Category category, PressHandler pressHandler) {
		this.keyMapping = new KeyMapping(
			translationKey,
			InputConstants.Type.KEYSYM,
			InputConstants.UNKNOWN.getValue(),
			category
		);

		this.pressHandler = pressHandler;
		Platform.getInstance().registerStartTickEvent(this::onStartTick);
	}

	public static KeyMappingHandler[] getHandlers() {
		return KEY_MAPPING_HANDLERS;
	}

	public KeyMapping keyMapping() {
		return this.keyMapping;
	}

	private void onStartTick() {
		if (this.keyMapping.isDown()) {
			if (!this.isHoldingKey) {
				this.pressHandler.handlePress();
			}
			this.isHoldingKey = true;
		} else {
			this.isHoldingKey = false;
		}
	}

	@FunctionalInterface
	private interface PressHandler {
		void handlePress();
	}
}
