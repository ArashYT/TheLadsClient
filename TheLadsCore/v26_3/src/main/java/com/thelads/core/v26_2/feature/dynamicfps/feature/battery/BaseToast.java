// Derived from Dynamic FPS 3.11.9 (MIT); see licenses/DynamicFPS-LICENSE.txt.
package com.thelads.core.v26_2.feature.dynamicfps.feature.battery;

import com.thelads.core.v26_2.feature.dynamicfps.util.ResourceLocations;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BaseToast implements Toast {
	private long firstRender;
	private Visibility visibility;

	protected Component title;
	protected Component description;
	protected @Nullable Identifier icon;


	protected BaseToast(Component title, Component description, @Nullable Identifier icon) {
		this.title = title;
		this.description = description;

		this.icon = icon;

		this.visibility = Visibility.SHOW;
	}

	@Override
	public @NotNull Visibility getWantedVisibility() {
		return this.visibility;
	}

	@Override
	public void update(ToastManager toastManager, long currentTime) {
		if (this.firstRender == 0) {
			return;
		}

		if (currentTime - this.firstRender >= 5000.0 * toastManager.getNotificationDisplayTimeMultiplier()) {
			this.visibility = Visibility.HIDE;
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, Font font, long currentTime) {
		if (this.firstRender == 0) {
			this.onFirstRender();
			this.firstRender = currentTime;
		}

		graphics.fill(0, 0, this.width(), this.height(), 0xf0202c40);
		graphics.fill(1, 1, this.width() - 1, this.height() - 1, 0xf00e1724);
		graphics.fill(0, 0, 2, this.height(), 0xff8eaff0);

		int x = 8;

		if (this.icon != null) {
			x += 22;

			BatteryDrawing.draw(graphics, 8, 8, BatteryTracker.charge(), com.thelads.core.v26_2.feature.dynamicfps.util.BatteryUtil.isCharging(BatteryTracker.status()));
		}

		graphics.text(font, this.title, x, 7, 0xffeef3ff, false);
		graphics.text(font, this.description, x, 18, 0xffa6b5cc, false);
	}

	public void onFirstRender() {}
}
