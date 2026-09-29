// Derived from Dynamic FPS 3.11.9 (MIT); see licenses/DynamicFPS-LICENSE.txt.
package com.thelads.core.v26_2.feature.dynamicfps.feature.state;

import com.thelads.core.v26_2.feature.dynamicfps.config.DynamicFPSConfig;
import com.thelads.core.v26_2.feature.dynamicfps.config.option.IgnoreInitialClick;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWMouseButtonCallback;
import org.lwjgl.glfw.GLFWWindowFocusCallback;

import java.time.Instant;

public class ClickIgnoreHandler {
	private final long address;
	private long focusedAt;

	private final GLFWWindowFocusCallback previousFocusCallback;
	private final GLFWMouseButtonCallback previousClickCallback;

	public ClickIgnoreHandler(long address) {
		this.address = address;

		this.previousFocusCallback = GLFW.glfwSetWindowFocusCallback(this.address, this::onFocusChanged);
		this.previousClickCallback = GLFW.glfwSetMouseButtonCallback(this.address, this::onMouseClicked);
	}

	public static boolean isFeatureActive() {
		return DynamicFPSConfig.INSTANCE.ignoreInitialClick() != IgnoreInitialClick.DISABLED;
	}

	private boolean shouldIgnoreClick() {
		Minecraft minecraft = Minecraft.getInstance();
		IgnoreInitialClick config = DynamicFPSConfig.INSTANCE.ignoreInitialClick();

		if (com.thelads.core.v26_2.feature.dynamicfps.DynamicFPSMod.isDisabled() || config == IgnoreInitialClick.DISABLED) {
			return false;
		}

		if (config == IgnoreInitialClick.IN_WORLD && minecraft.gui.screen() != null) {
			return false;
		}

		return this.focusedAt + 10 >= Instant.now().toEpochMilli();
	}

	private void onFocusChanged(long address, boolean focused) {
		if (this.isCurrentWindow(address) && focused) {
			this.focusedAt = Instant.now().toEpochMilli();
		}

		if (this.previousFocusCallback != null) {
			this.previousFocusCallback.invoke(address, focused);
		}
	}

	private void onMouseClicked(long window, int button, int action, int mods) {
		if (this.isCurrentWindow(window) && shouldIgnoreClick()) {
			return;
		}

		if (this.previousClickCallback != null) {
			this.previousClickCallback.invoke(window, button, action, mods);
		}
	}

	private boolean isCurrentWindow(long address) {
		return address == this.address;
	}
}
