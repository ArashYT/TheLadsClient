// Adapted from World Play Time Reborn 1.2.6 by KoroWin, based on World Play Time by Khajiitos (MIT); modified by The Lads: ported to Minecraft 1.20.1 from the Lads 26.3 embedded copy.
package com.thelads.core.v1_20_1.embedded.playtime.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.core.v1_20_1.embedded.playtime.config.WptConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelSummary;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Reads a world's play time (from the stats files) and size (all files of the world folder) and attaches them to its
 * {@link LevelSummary}. This is the body of the 26.3 {@code LevelStorageSourceMixin#onMakeLevelSummary}, moved here because
 * 1.20.1 builds summaries in a different place (see the mixin).
 */
public final class PlayTimeScanner {
	private PlayTimeScanner() {
	}

	/** Wraps the task that reads one world's summary; the result is the same summary with play time and size attached. */
	public static Supplier<LevelSummary> wrap(Supplier<LevelSummary> reader, Path levelDirectory) {
		return () -> {
			LevelSummary summary = reader.get();
			if (summary != null) {
				try {
					attach(summary, levelDirectory);
				} catch (RuntimeException ignored) {
					// a broken stats file must never keep a world out of the list
				}
			}
			return summary;
		};
	}

	/**
	 * By default, only the local player's own stats are counted, so that time spent by other players who played this world
	 * (e.g. through LAN) is not added on top. If the local player has no stats file in this world, the playtime of all
	 * players is shown instead, so the indicator doesn't disappear on worlds that were only played by other accounts.
	 * Summing all players unconditionally can be enabled in the config.
	 */
	public static void attach(LevelSummary summary, Path levelDirectory) {
		if (summary instanceof LevelSummary.SymlinkLevelSummary || !(summary instanceof IWithPlayTime withPlayTime)) {
			return;
		}

		if (WptConfig.showWorldSize.get() && Files.isDirectory(levelDirectory)) {
			long[] worldSize = {0};
			try {
				Files.walkFileTree(levelDirectory, new SimpleFileVisitor<>() {
					@Override
					public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
						worldSize[0] += attributes.size();
						return FileVisitResult.CONTINUE;
					}

					@Override
					public FileVisitResult visitFileFailed(Path file, IOException exception) {
						return FileVisitResult.CONTINUE;
					}
				});
				withPlayTime.setWorldSizeBytes(worldSize[0]);
			} catch (IOException ignored) {
			}
		}

		File statsFile = levelDirectory.resolve(LevelResource.PLAYER_STATS_DIR.getId()).toFile();
		if (!statsFile.isDirectory()) {
			return;
		}
		File[] saveFiles = statsFile.listFiles();
		if (saveFiles == null) {
			return;
		}

		int totalPlayTime = 0;
		if (!WptConfig.worldPlayTimeCountAllPlayers.get()) {
			File playerStatsFile = findPlayerStatsFile(statsFile);
			if (playerStatsFile != null) {
				totalPlayTime = readPlayTime(playerStatsFile);
			}
		}
		if (totalPlayTime <= 0) {
			for (File file : saveFiles) {
				totalPlayTime += readPlayTime(file);
			}
		}
		if (totalPlayTime > 0) {
			withPlayTime.setPlayTimeTicks(totalPlayTime);
		}
	}

	/** Returns the stats file of the local player, or null if the player has no stats in this world. */
	private static File findPlayerStatsFile(File statsDirectory) {
		try {
			UUID uuid = Minecraft.getInstance().getUser().getProfileId();
			File statsFile = new File(statsDirectory, uuid.toString() + ".json");
			return statsFile.isFile() ? statsFile : null;
		} catch (RuntimeException invalidProfileId) {
			return null;
		}
	}

	/** Reads the minecraft:play_time stat from a stats file, or 0 if it's missing. */
	private static int readPlayTime(File file) {
		if (!file.isFile() || !file.getName().endsWith(".json")) {
			return 0;
		}
		try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
			JsonObject jsonObject = JsonParser.parseReader(reader).getAsJsonObject();

			if (jsonObject.has("stats")) {
				JsonObject statsObject = jsonObject.getAsJsonObject("stats");

				if (statsObject.has("minecraft:custom")) {
					JsonObject customObject = statsObject.getAsJsonObject("minecraft:custom");

					if (customObject.has("minecraft:play_time")) {
						return customObject.get("minecraft:play_time").getAsInt();
					}
				}
			}
		} catch (IOException | RuntimeException ignored) {
		}

		return 0;
	}
}
