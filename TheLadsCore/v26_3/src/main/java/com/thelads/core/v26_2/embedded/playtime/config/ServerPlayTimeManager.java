// Adapted from World Play Time Reborn 1.2.6 by KoroWin, based on World Play Time by Khajiitos (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.playtime.config;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import com.thelads.core.v26_2.embedded.playtime.WorldPlayTimeReborn;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class ServerPlayTimeManager {
	private static final File FILE = new File("config/worldplaytimereborn/servers_playtime.dat");
	private static final HashMap<String, Integer> SERVER_PLAY_TIMES = new HashMap<>();

	/**
	 * Saves the server playtime table to disk.
	 */
	public static void save() {
		if (SERVER_PLAY_TIMES.isEmpty()) {
			return;
		}

		File parent = FILE.getParentFile();
		if (!parent.isDirectory() && !parent.mkdirs()) {
			WorldPlayTimeReborn.LOGGER.error("Failed to create config/worldplaytimereborn/ directory");
			return;
		}

		CompoundTag compoundTag = new CompoundTag();

		for (Map.Entry<String, Integer> entry : SERVER_PLAY_TIMES.entrySet()) {
			String serverIp = entry.getKey();
			int playTime = entry.getValue();

			compoundTag.putInt(serverIp, playTime);
		}

		try {
			NbtIo.write(compoundTag, FILE.toPath());
		} catch (IOException e) {
			WorldPlayTimeReborn.LOGGER.error("Failed to write servers_playtime.dat", e);
		}
	}

	/**
	 * Loads the server playtime table from disk.
	 */
	public static void load() {
		if (!FILE.exists()) {
			return;
		}

		try {
			CompoundTag compoundTag = NbtIo.read(FILE.toPath());

			if (compoundTag == null) {
				return;
			}

			for (String serverIp : compoundTag.keySet()) {
				Optional<Integer> playTimeOpt = compoundTag.getInt(serverIp);

				if (playTimeOpt.isEmpty()) {
					continue;
				}

				int playTime = playTimeOpt.get();

				if (playTime > 0) {
					SERVER_PLAY_TIMES.put(serverIp, playTime);
				}
			}
		} catch (IOException e) {
			WorldPlayTimeReborn.LOGGER.error("Failed to read servers_playtime.dat", e);
		}
	}

	/**
	 * Saves server playtime table asynchronously.
	 */
	public static void saveAsync() {
		CompletableFuture.runAsync(ServerPlayTimeManager::save);
	}

	/**
	 * Increments playtime for a given server by one tick.
	 */
	public static void onPlayTick(String serverIp) {
		SERVER_PLAY_TIMES.put(serverIp, SERVER_PLAY_TIMES.getOrDefault(serverIp, 0) + 1);
	}

	/**
	 * Returns playtime in ticks for a server.
	 */
	public static int getPlayTime(String serverIp) {
		return SERVER_PLAY_TIMES.getOrDefault(serverIp, 0);
	}
}
