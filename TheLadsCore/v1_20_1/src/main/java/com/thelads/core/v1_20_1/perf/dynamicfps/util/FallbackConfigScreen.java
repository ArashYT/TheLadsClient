// Derived from Dynamic FPS 3.11.9 (MIT); see licenses/DynamicFPS-LICENSE.txt.
package com.thelads.core.v1_20_1.perf.dynamicfps.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class FallbackConfigScreen extends Screen {
	private final Screen parent;

	private static final Component WARNING_0 = Components.translatable("config", "warn_cloth_config.0");
	private static final Component WARNING_1 = Components.translatable("config", "warn_cloth_config.1");

	public FallbackConfigScreen(Screen parent) {
		super(Components.translatable("config", "title"));

		this.parent = parent;
	}

	@Override
	protected void init() {
		int width = 152;
		int height = 20;
		int x = (this.width - width) / 2;
		int y = this.height - height - 5;

		this.addRenderableWidget(
			Button.builder(CommonComponents.GUI_BACK, button -> this.onClose()).bounds(x, y, width, height).build()
		);
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
		this.renderBackground(guiGraphics);
		super.render(guiGraphics, mouseX, mouseY, partialTicks);

		int width = guiGraphics.guiWidth() / 2;
		int height = guiGraphics.guiHeight() / 3;

		guiGraphics.drawCenteredString(this.font, WARNING_0, width, height, -1);
		guiGraphics.drawCenteredString(this.font, WARNING_1, width, height + 10, -1);
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().setScreen(this.parent);
	}
}
