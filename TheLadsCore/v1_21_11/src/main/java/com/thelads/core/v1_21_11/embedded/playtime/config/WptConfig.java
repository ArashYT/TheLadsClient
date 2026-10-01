// Adapted from World Play Time Reborn 1.2.6 by KoroWin, based on World Play Time by Khajiitos (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_11.embedded.playtime.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.thelads.core.v1_21_11.embedded.playtime.WorldPlayTimeReborn;
import com.thelads.core.v1_21_11.embedded.playtime.util.Color;
import com.thelads.core.v1_21_11.embedded.playtime.util.ServerEntryRenderPos;
import com.thelads.core.v1_21_11.embedded.playtime.util.WorldEntryRenderPos;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;

public class WptConfig {
	private static final File FILE = new File("config/worldplaytimereborn/config.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	@Entry
	public static final WptConfigValues.BooleanValue showServerPlayTime = new WptConfigValues.BooleanValue(false);

	@Entry
	public static final WptConfigValues.EnumValue<ServerEntryRenderPos> serverPlayTimePosition = new WptConfigValues.EnumValue<>(ServerEntryRenderPos.AFTER_NAME);

	@Entry
	public static final WptConfigValues.ColorValue serverPlayTimeColor = new WptConfigValues.ColorValue(new Color(128, 128, 128, 255));

	@Entry
	public static final WptConfigValues.BooleanValue showWorldPlayTime = new WptConfigValues.BooleanValue(true);

	@Entry
	public static final WptConfigValues.BooleanValue worldPlayTimeCountAllPlayers = new WptConfigValues.BooleanValue(false);

	@Entry
	public static final WptConfigValues.BooleanValue showWorldSize = new WptConfigValues.BooleanValue(true);

	@Entry
	public static final WptConfigValues.EnumValue<WorldEntryRenderPos> worldPlayTimePosition = new WptConfigValues.EnumValue<>(WorldEntryRenderPos.TOP_RIGHT);

	@Entry
	public static final WptConfigValues.ColorValue worldPlayTimeColor = new WptConfigValues.ColorValue(new Color(128, 128, 128, 255));

	/**
	 * Loads config from disk or creates it with defaults if it doesn't exist.
	 */
	public static void init() {
		if (!FILE.exists()) {
			save();
		} else {
			load();
		}
	}

	/**
	 * Saves current config to disk.
	 */
	public static void save() {
		File parent = FILE.getParentFile();
		if (!parent.isDirectory() && !parent.mkdirs()) {
			WorldPlayTimeReborn.LOGGER.error("Failed to create config/worldplaytimereborn/ directory");
			return;
		}

		try (FileWriter fileWriter = new FileWriter(FILE)) {
			JsonObject jsonObject = new JsonObject();

			for (Field field : WptConfig.class.getDeclaredFields()) {
				if (!field.isAnnotationPresent(Entry.class)) {
					continue;
				}

				Object object = field.get(null);

				if (!(object instanceof WptConfigValues.Value<?> configValue)) {
					continue;
				}

				jsonObject.add(field.getName(), configValue.write());
			}

			GSON.toJson(jsonObject, fileWriter);
		} catch (IOException e) {
			WorldPlayTimeReborn.LOGGER.error("Failed to save the World Play Time Reborn config", e);
		} catch (IllegalAccessException e) {
			WorldPlayTimeReborn.LOGGER.error("Error while saving the World Play Time Reborn config", e);
		}
	}

	/**
	 * Loads config from disk.
	 */
	public static void load() {
		if (!FILE.exists()) {
			return;
		}

		try (FileReader fileReader = new FileReader(FILE)) {
			JsonObject jsonObject = GSON.fromJson(fileReader, JsonObject.class);

			for (Field field : WptConfig.class.getDeclaredFields()) {
				if (!field.isAnnotationPresent(Entry.class)) {
					continue;
				}

				String fieldName = field.getName();

				if (!jsonObject.has(fieldName)) {
					continue;
				}

				Object object = field.get(null);

				if (!(object instanceof WptConfigValues.Value<?> configValue)) {
					continue;
				}

				JsonElement jsonElement = jsonObject.get(fieldName);
				configValue.setUnchecked(configValue.read(jsonElement));
			}
		} catch (IOException e) {
			WorldPlayTimeReborn.LOGGER.error("Failed to read the World Play Time Reborn config", e);
		} catch (IllegalAccessException e) {
			WorldPlayTimeReborn.LOGGER.error("Error while reading the World Play Time Reborn config", e);
		}
	}

	@Retention(RetentionPolicy.RUNTIME)
	private @interface Entry {
	}
}
