// Adapted from World Play Time Reborn 1.2.6 by KoroWin, based on World Play Time by Khajiitos (MIT); modified by The Lads: ported to Minecraft 1.20.1 from the Lads 26.3 embedded copy.
package com.thelads.core.v1_20_1.embedded.playtime.config;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import com.thelads.core.v1_20_1.embedded.playtime.WorldPlayTimeReborn;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ServerPlayTimeManager {
	private static final File FILE = new File("config/worldplaytimereborn/servers_playtime.dat");
	private static final Object WRITE_LOCK = new Object();
	private static final HashMap<String, Integer> SERVER_PLAY_TIMES = new HashMap<>();

	/**
	 * Saves the server playtime table to disk.
	 */
	public static void save() {
		CompoundTag compoundTag;
		synchronized (ServerPlayTimeManager.class) {
			if (SERVER_PLAY_TIMES.isEmpty()) {
				return;
			}
			compoundTag = new CompoundTag();
			for (Map.Entry<String, Integer> entry : SERVER_PLAY_TIMES.entrySet()) {
				compoundTag.putInt(entry.getKey(), entry.getValue());
			}
		}

		File parent = FILE.getParentFile();
		if (!parent.isDirectory() && !parent.mkdirs()) {
			WorldPlayTimeReborn.LOGGER.error("Failed to create config/worldplaytimereborn/ directory");
			return;
		}

		synchronized (WRITE_LOCK) {
			try {
				writeAtomically(compoundTag);
			} catch (IOException e) {
				WorldPlayTimeReborn.LOGGER.error("Failed to write servers_playtime.dat", e);
			}
		}
	}

	/** Lads: written beside the file and moved over it, so a crash during the write keeps the old table. */
	private static void writeAtomically(CompoundTag compoundTag) throws IOException {
		Path target = FILE.toPath();
		Path temp = target.resolveSibling(FILE.getName() + ".tmp");
		NbtIo.write(compoundTag, temp.toFile());
		try {
			Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException unsupported) {
			Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	/**
	 * Loads the server playtime table from disk.
	 */
	public static synchronized void load() {
		if (!FILE.exists()) {
			return;
		}

		try {
			CompoundTag compoundTag = NbtIo.read(FILE);

			if (compoundTag == null) {
				return;
			}

			for (String serverIp : compoundTag.getAllKeys()) {
				if (!compoundTag.contains(serverIp, Tag.TAG_ANY_NUMERIC)) {
					continue;
				}

				int playTime = compoundTag.getInt(serverIp);

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
		CompletableFuture.runAsync(ServerPlayTimeManager::save).exceptionally(failure -> {
			WorldPlayTimeReborn.LOGGER.error("Failed to save servers_playtime.dat", failure);
			return null;
		});
	}

	/**
	 * Increments playtime for a given server by one tick.
	 */
	public static synchronized void onPlayTick(String serverIp) {
		SERVER_PLAY_TIMES.put(serverIp, SERVER_PLAY_TIMES.getOrDefault(serverIp, 0) + 1);
	}

	/**
	 * Returns playtime in ticks for a server.
	 */
	public static synchronized int getPlayTime(String serverIp) {
		return SERVER_PLAY_TIMES.getOrDefault(serverIp, 0);
	}
}
