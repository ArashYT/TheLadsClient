// Adapted from World Play Time Reborn 1.2.6 by KoroWin, based on World Play Time by Khajiitos (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.playtime.util;

import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Color {
	private static final Pattern SAVE_PATTERN = Pattern.compile("\\((\\d+), (\\d+), (\\d+), (\\d+)\\)");
	public int r;
	public int g;
	public int b;
	public int a;

	/**
	 * Creates a new RGBA color.
	 */
	public Color(int r, int g, int b, int a) {
		this.r = r;
		this.g = g;
		this.b = b;
		this.a = a;
	}

	/**
	 * Serializes this color to a string for config storage.
	 */
	@Override
	public String toString() {
		return String.format("(%d, %d, %d, %d)", this.r, this.g, this.b, this.a);
	}

	/**
	 * Parses a color from config storage string.
	 */
	public static @Nullable Color fromString(String string) {
		Matcher matcher = SAVE_PATTERN.matcher(string);

		if (matcher.matches()) {
			try {
				int r = Integer.parseInt(matcher.group(1));
				int g = Integer.parseInt(matcher.group(2));
				int b = Integer.parseInt(matcher.group(3));
				int a = Integer.parseInt(matcher.group(4));

				return new Color(r, g, b, a < 0 ? 127 - a : a);
			} catch (NumberFormatException ignored) {
			}
		}

		return null;
	}

	/**
	 * Creates a color from packed ARGB integer.
	 */
	public static Color fromARGB(int argb) {
		int r = (argb & 0x00FF0000) >> 16;
		int g = (argb & 0x0000FF00) >> 8;
		int b = (argb & 0x000000FF);
		int a = (argb >> 24) & 0xFF;

		return new Color(r, g, b, a);
	}

	/**
	 * Converts this color to packed ARGB integer.
	 */
	public int toARGB() {
		return (a << 24) | (r << 16) | (g << 8) | b;
	}
}
