// Adapted from World Play Time Reborn 1.2.6 by KoroWin, based on World Play Time by Khajiitos (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.playtime.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import com.thelads.core.v26_2.embedded.playtime.WorldPlayTimeReborn;
import com.thelads.core.v26_2.embedded.playtime.util.Color;

import java.util.Locale;

public class PlayTimeRenderer {
	private static final net.minecraft.resources.Identifier TIME_ICON = WorldPlayTimeReborn.id("textures/gui/time_icon.png");

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
	public static void render(GuiGraphicsExtractor guiGraphics, int x, int y, int playTimeTicks, Color color) {
		Minecraft minecraft = Minecraft.getInstance();
		Component component = getPlayTimeComponent(playTimeTicks);

		if (component == null) {
			return;
		}

		guiGraphics.blit(RenderPipelines.GUI_TEXTURED, TIME_ICON, x, y, 0.f, 0.f, 9, 9, 9, 9, color.toARGB());
		guiGraphics.text(minecraft.font, component, x + 11, y + 1, color.toARGB(), false);
	}

	/**
	 * Renders the world size text.
	 */
	public static void renderWorldSize(GuiGraphicsExtractor guiGraphics, int x, int y, long bytes, Color color) {
		Component component = getWorldSizeComponent(bytes);
		if (component != null) {
			guiGraphics.text(Minecraft.getInstance().font, component, x, y + 1, color.toARGB(), false);
		}
	}
}
