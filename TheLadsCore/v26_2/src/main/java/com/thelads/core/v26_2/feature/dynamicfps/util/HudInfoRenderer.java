// Derived from Dynamic FPS 3.11.9 (MIT); see licenses/DynamicFPS-LICENSE.txt.
package com.thelads.core.v26_2.feature.dynamicfps.util;

import com.thelads.core.v26_2.feature.dynamicfps.config.BatteryTrackerConfig;
import com.thelads.core.v26_2.feature.dynamicfps.config.DynamicFPSConfig;
import com.thelads.core.v26_2.feature.dynamicfps.feature.battery.BatteryTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

import net.minecraft.resources.Identifier;

import com.thelads.core.v26_2.feature.dynamicfps.DynamicFPSMod;

public final class HudInfoRenderer {
	public static void renderInfo(GuiGraphicsExtractor guiGraphics) {
		Minecraft minecraft = Minecraft.getInstance();

		if (com.thelads.core.v26_2.feature.dynamicfps.LadsBackgroundBridge.disabled() || minecraft.gui.hud.isHidden() || minecraft.gui.screen() != null) {
			return;
		}

		if (DynamicFPSConfig.INSTANCE.batteryTracker().enabled()) {
			drawBatteryOverlay(guiGraphics);
		}

		if (DynamicFPSMod.disabledByUser()) {
			drawCenteredText(guiGraphics, Components.translatable("gui", "hud.disabled"));
		} else if (DynamicFPSMod.isForcingLowFPS()) {
			drawCenteredText(guiGraphics, Components.translatable("gui", "hud.reducing"));
		}
	}

	private static void drawCenteredText(GuiGraphicsExtractor guiGraphics, Component component) {
		int width = guiGraphics.guiWidth() / 2;
		Minecraft minecraft = Minecraft.getInstance();

		guiGraphics.centeredText(minecraft.font, component, width, 32, -1);
	}

	private static void drawBatteryOverlay(GuiGraphicsExtractor graphics) {
		Minecraft minecraft = Minecraft.getInstance();
		BatteryTrackerConfig config = DynamicFPSConfig.INSTANCE.batteryTracker();

		if ((!config.showWhenDebug() && minecraft.debugEntries.isOverlayVisible()) || !BatteryTracker.hasBatteries()) {
			return;
		}

		if (!config.display().condition().isConditionMet()) {
			return;
		}

		// pair of coordinates
		int[] position = config.display().placement().get(minecraft.getWindow());

		// resource, x, y, z, ?, ?, width, height, width, height
		com.thelads.core.v26_2.feature.dynamicfps.feature.battery.BatteryDrawing.draw(graphics, position[0], position[1], BatteryTracker.charge(), BatteryUtil.isCharging(BatteryTracker.status()));
		graphics.text(minecraft.font, BatteryTracker.charge() + "%", position[0] + 20, position[1] + 4, 0xffffffff, true);
	}
}
