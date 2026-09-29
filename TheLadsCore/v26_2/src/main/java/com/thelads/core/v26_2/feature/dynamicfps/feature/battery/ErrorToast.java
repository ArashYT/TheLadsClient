// Derived from Dynamic FPS 3.11.9 (MIT); see licenses/DynamicFPS-LICENSE.txt.
package com.thelads.core.v26_2.feature.dynamicfps.feature.battery;

import com.thelads.core.v26_2.feature.dynamicfps.util.Components;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class ErrorToast extends BaseToast {
	private static final Component TITLE = Components.translatable("toast", "error");

	private ErrorToast(Component description) {
		super(TITLE, description, null);
	}

	/**
	 * Queue some information to be shown as a toast.
	 */
	public static void queueToast(Component description) {
		ErrorToast toast = new ErrorToast(description);
		Minecraft.getInstance().gui.toastManager().addToast(toast);
	}
}
