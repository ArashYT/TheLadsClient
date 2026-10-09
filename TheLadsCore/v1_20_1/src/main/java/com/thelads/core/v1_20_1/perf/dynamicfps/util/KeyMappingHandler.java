// Derived from Dynamic FPS 3.11.9 (MIT); see licenses/DynamicFPS-LICENSE.txt.
package com.thelads.core.v1_20_1.perf.dynamicfps.util;

import com.mojang.blaze3d.platform.InputConstants;
import com.thelads.core.v1_20_1.perf.dynamicfps.DynamicFPSMod;
import com.thelads.core.v1_20_1.perf.dynamicfps.service.Platform;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;

public final class KeyMappingHandler {
	private final KeyMapping keyMapping;
	private boolean isHoldingKey = false;
	private final PressHandler pressHandler;

	private static KeyMappingHandler[] handlers;

	private KeyMappingHandler(String translationKey, String category, PressHandler pressHandler) {
		this.keyMapping = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			translationKey,
			InputConstants.Type.KEYSYM,
			InputConstants.UNKNOWN.getValue(),
			category
		));

		this.pressHandler = pressHandler;
		Platform.getInstance().registerStartTickEvent(this::onStartTick);
	}

	/** Registers the two key bindings (unbound by default). Call once, from the client initializer. */
	public static synchronized void register() {
		if (handlers != null) {
			return;
		}

		handlers = new KeyMappingHandler[] {
			new KeyMappingHandler(Components.translationKey("key", "toggle_forced"), KeyMapping.CATEGORY_MISC, DynamicFPSMod::toggleForceLowFPS),
			new KeyMappingHandler(Components.translationKey("key", "toggle_disabled"), KeyMapping.CATEGORY_MISC, DynamicFPSMod::toggleDisabled)
		};
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
