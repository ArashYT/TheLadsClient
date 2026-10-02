// Adapted from World Play Time Reborn 1.2.6 by KoroWin, based on World Play Time by Khajiitos (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.playtime.util;

public interface IWithPlayTime {
	/**
	 * Sets the cached playtime in ticks.
	 */
	void setPlayTimeTicks(int playTimeTicks);

	/**
	 * Gets the cached playtime in ticks.
	 */
	int getPlayTimeTicks();

	/**
	 * Sets the cached world size in bytes.
	 */
	void setWorldSizeBytes(long worldSizeBytes);

	/**
	 * Gets the cached world size in bytes.
	 */
	long getWorldSizeBytes();
}
