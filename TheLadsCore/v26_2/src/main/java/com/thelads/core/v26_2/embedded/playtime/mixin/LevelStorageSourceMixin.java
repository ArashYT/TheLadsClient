// Adapted from World Play Time Reborn 1.2.6 by KoroWin, based on World Play Time by Khajiitos (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.playtime.mixin;

import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Dynamic;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.thelads.core.v26_2.embedded.playtime.config.WptConfig;
import com.thelads.core.v26_2.embedded.playtime.util.IWithPlayTime;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.UUID;

@Mixin(LevelStorageSource.class)
public class LevelStorageSourceMixin {
	/**
	 * Reads playtime from stats files and attaches it to the LevelSummary object.
	 * <p>
	 * By default, only the local player's own stats are counted, so that time spent
	 * by other players who played this world (e.g. through LAN) is not added on top.
	 * If the local player has no stats file in this world, the playtime of all
	 * players is shown instead, so the indicator doesn't disappear on worlds that
	 * were only played by other accounts. Summing all players unconditionally can
	 * be enabled in the mod config.
	 */
	@Inject(at = @At("RETURN"), method = "makeLevelSummary")
	public void onMakeLevelSummary(Dynamic<?> dynamic, LevelStorageSource.LevelDirectory levelDirectory, boolean idk, int idk2, CallbackInfoReturnable<LevelSummary> cir) {
		LevelSummary levelSummary = cir.getReturnValue();

		if (levelSummary instanceof IWithPlayTime withPlayTime) {
			if (WptConfig.showWorldSize.get() && Files.isDirectory(levelDirectory.path())) {
				long[] worldSize = {0};
				try {
					Files.walkFileTree(levelDirectory.path(), new SimpleFileVisitor<>() {
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

			Path stats = levelDirectory.resourcePath(LevelResource.PLAYER_STATS_DIR);
			File statsFile = stats.toFile();

			if (statsFile.isDirectory()) {
				File[] saveFiles = statsFile.listFiles();

				if (saveFiles != null) {
					int totalPlayTime = 0;

					if (!WptConfig.worldPlayTimeCountAllPlayers.get()) {
						File playerStatsFile = worldplaytimereborn$findPlayerStatsFile(statsFile);

						if (playerStatsFile != null) {
							totalPlayTime = worldplaytimereborn$readPlayTime(playerStatsFile);
						}
					}

					if (totalPlayTime <= 0) {
						for (File file : saveFiles) {
							totalPlayTime += worldplaytimereborn$readPlayTime(file);
						}
					}

					if (totalPlayTime > 0) {
						withPlayTime.setPlayTimeTicks(totalPlayTime);
					}
				}
			}
		}
	}

	/**
	 * Returns the stats file of the local player, or null if the player has no stats in this world.
	 */
	@Unique
	private static File worldplaytimereborn$findPlayerStatsFile(File statsDirectory) {
		UUID uuid = Minecraft.getInstance().getUser().getProfileId();

		File statsFile = new File(statsDirectory, uuid.toString() + ".json");

		return statsFile.isFile() ? statsFile : null;
	}

	/**
	 * Reads the minecraft:play_time stat from a stats file, or 0 if it's missing.
	 */
	@Unique
	private static int worldplaytimereborn$readPlayTime(File file) {
		try (FileReader fileReader = new FileReader(file)) {
			JsonObject jsonObject = JsonParser.parseReader(fileReader).getAsJsonObject();

			if (jsonObject.has("stats")) {
				JsonObject statsObject = jsonObject.getAsJsonObject("stats");

				if (statsObject.has("minecraft:custom")) {
					JsonObject customObject = statsObject.getAsJsonObject("minecraft:custom");

					if (customObject.has("minecraft:play_time")) {
						return customObject.get("minecraft:play_time").getAsInt();
					}
				}
			}
		} catch (JsonIOException | IOException | IllegalStateException ignored) {
		}

		return 0;
	}
}
