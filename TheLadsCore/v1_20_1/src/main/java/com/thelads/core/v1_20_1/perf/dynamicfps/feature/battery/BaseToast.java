// Derived from Dynamic FPS 3.11.9 (MIT); see licenses/DynamicFPS-LICENSE.txt.
package com.thelads.core.v1_20_1.perf.dynamicfps.feature.battery;

import com.thelads.core.v1_20_1.perf.dynamicfps.util.BatteryUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public class BaseToast implements Toast {
	private long firstRender;

	protected Component title;
	protected Component description;
	protected @Nullable ResourceLocation icon;

	protected BaseToast(Component title, Component description, @Nullable ResourceLocation icon) {
		this.title = title;
		this.description = description;
		this.icon = icon;
	}

	@Override
	public Visibility render(GuiGraphics graphics, ToastComponent toasts, long currentTime) {
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

			BatteryDrawing.draw(graphics, 8, 8, BatteryTracker.charge(), BatteryUtil.isCharging(BatteryTracker.status()));
		}

		var font = toasts.getMinecraft().font;
		graphics.drawString(font, this.title, x, 7, 0xffeef3ff, false);
		graphics.drawString(font, this.description, x, 18, 0xffa6b5cc, false);

		return currentTime - this.firstRender >= 5000.0 * toasts.getNotificationDisplayTimeMultiplier() ? Visibility.HIDE : Visibility.SHOW;
	}

	public void onFirstRender() {}
}
