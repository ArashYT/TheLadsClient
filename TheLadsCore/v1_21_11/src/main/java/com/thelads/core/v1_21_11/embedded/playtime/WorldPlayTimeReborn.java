// Adapted from World Play Time Reborn 1.2.6 by KoroWin, based on World Play Time by Khajiitos (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_11.embedded.playtime;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.thelads.core.v1_21_11.embedded.playtime.config.ServerPlayTimeManager;
import com.thelads.core.v1_21_11.embedded.playtime.config.WptConfig;

public final class WorldPlayTimeReborn {
	public static final String MOD_ID = "worldplaytimereborn";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private WorldPlayTimeReborn() {
	}

	/**
	 * Initializes config and persistent playtime storage.
	 */
	public static void init() {
		WptConfig.init();
		ServerPlayTimeManager.load();
	}

	/**
	 * Creates a namespaced identifier under this mod id.
	 */
	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
