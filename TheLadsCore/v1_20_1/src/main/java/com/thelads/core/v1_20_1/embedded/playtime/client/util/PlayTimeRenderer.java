// Adapted from World Play Time Reborn 1.2.6 by KoroWin, based on World Play Time by Khajiitos (MIT); modified by The Lads: ported to Minecraft 1.20.1 from the Lads 26.3 embedded copy.
package com.thelads.core.v1_20_1.embedded.playtime.client.util;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import com.thelads.core.v1_20_1.embedded.playtime.WorldPlayTimeReborn;
import com.thelads.core.v1_20_1.embedded.playtime.util.Color;

import java.util.Locale;

public class PlayTimeRenderer {
	private static final net.minecraft.resources.ResourceLocation TIME_ICON = WorldPlayTimeReborn.id("textures/gui/time_icon.png");

	/**
	 * Builds a localized component representing playtime in hours.
	 */
	public static @Nullable Component getPlayTimeComponent(int ticks) {
		if (ticks <= 0) {
			return null;
		}

		double hours = (ticks / 20.0) / 3600.0;

		return Component.translatable(
				"worldplaytimereborn.format",
				Component.literal(hours >= 100.0 ? String.valueOf((int) hours) : String.format(Locale.US, "%.1f", hours))
		);
	}

	/**
	 * Returns total pixel width of the rendered indicator (icon + text).
	 */
	public static int getWholeWidth(int ticks) {
		Component component = getPlayTimeComponent(ticks);

		if (component == null) {
			return 0;
		}

		Minecraft minecraft = Minecraft.getInstance();

		return minecraft.font.width(component) + 11;
	}

	/**
	 * Builds a component representing a byte size with binary units.
	 */
	public static @Nullable Component getWorldSizeComponent(long bytes) {
		if (bytes < 0) {
			return null;
		}

		String[] units = {"B", "KB", "MB", "GB", "TB"};
		double size = bytes;
		int unit = 0;

		while (size >= 1024.0 && unit < units.length - 1) {
			size /= 1024.0;
			unit++;
		}

		return Component.literal(unit == 0 ? bytes + " B" : String.format(Locale.US, "%.1f %s", size, units[unit]));
	}

	/**
	 * Returns the rendered width of the world size text.
	 */
	public static int getWorldSizeWidth(long bytes) {
		Component component = getWorldSizeComponent(bytes);
		return component == null ? 0 : Minecraft.getInstance().font.width(component);
	}


	/**
	 * Renders the indicator with icon and formatted time.
	 */
	public static void render(GuiGraphics guiGraphics, int x, int y, int playTimeTicks, Color color) {
		Minecraft minecraft = Minecraft.getInstance();
		Component component = getPlayTimeComponent(playTimeTicks);

		if (component == null) {
			return;
		}

		RenderSystem.enableBlend();
		guiGraphics.setColor(color.r / 255.0F, color.g / 255.0F, color.b / 255.0F, color.a / 255.0F);
		guiGraphics.blit(TIME_ICON, x, y, 0.0F, 0.0F, 9, 9, 9, 9);
		guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
		RenderSystem.disableBlend();
		guiGraphics.drawString(minecraft.font, component, x + 11, y + 1, color.toARGB(), false);
	}

	/**
	 * Renders the world size text.
	 */
	public static void renderWorldSize(GuiGraphics guiGraphics, int x, int y, long bytes, Color color) {
		Component component = getWorldSizeComponent(bytes);
		if (component != null) {
			guiGraphics.drawString(Minecraft.getInstance().font, component, x, y + 1, color.toARGB(), false);
		}
	}
}
