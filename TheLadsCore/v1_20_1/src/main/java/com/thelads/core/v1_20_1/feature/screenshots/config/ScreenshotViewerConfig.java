// SPDX-License-Identifier: MIT
// Adapted from Screenshot Viewer 1.3.6 (LGatodu47). See ScreenshotViewer-LICENSE.txt.
package com.thelads.core.v1_20_1.feature.screenshots.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Mth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The gallery options, kept in config/theladscore/screenshot_viewer.json (flat: option name to value).
 * Settings of the standalone Screenshot Viewer mod (config/screenshot_viewer-client.json, nested by category) are read once
 * when this file does not exist yet; nothing in that file is changed.
 */
public final class ScreenshotViewerConfig implements ConfigAccess {
   private static final Logger LOGGER = LoggerFactory.getLogger("Lads Screenshots");
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private final Path file = FabricLoader.getInstance().getConfigDir().resolve("theladscore").resolve("screenshot_viewer.json");
   private final Map<String, Object> values = new ConcurrentHashMap<>();

   public ScreenshotViewerConfig() {
      if (Files.isRegularFile(file)) {
         load(file, false);
      } else {
         load(FabricLoader.getInstance().getConfigDir().resolve("screenshot_viewer-client.json"), true);
      }
   }

   @Override
   public <T> Optional<T> get(ConfigOption<T> option) {
      Object stored = values.get(option.name());
      if (stored != null) {
         return Optional.of(option.type().cast(stored));
      }
      return Optional.ofNullable(option.defaultValue());
   }

   @Override
   public <T> void put(ConfigOption<T> option, T value) {
      if (value == null) {
         values.remove(option.name());
      } else {
         values.put(option.name(), clamp(option, value));
      }
   }

   @SuppressWarnings("unchecked")
   private static <T> T clamp(ConfigOption<T> option, T value) {
      if (value instanceof Integer number && option.min() != null && option.max() != null) {
         return (T) Integer.valueOf(Mth.clamp(number, option.min(), option.max()));
      }
      return value;
   }

   /** Writes through a temporary file, so a crash never leaves half a settings file behind. */
   public synchronized void writeToFile() {
      JsonObject root = new JsonObject();
      for (ConfigOption<?> option : ScreenshotViewerOptions.all()) {
         Object value = values.get(option.name());
         if (value != null) {
            root.add(option.name(), write(option, value));
         }
      }
      try {
         Files.createDirectories(file.getParent());
         Path temporary = Files.createTempFile(file.getParent(), "screenshot_viewer", ".tmp");
         try {
            Files.writeString(temporary, GSON.toJson(root), StandardCharsets.UTF_8);
            try {
               Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException noAtomicMove) {
               Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
         } finally {
            Files.deleteIfExists(temporary);
         }
      } catch (IOException failure) {
         LOGGER.warn("Could not save the gallery settings", failure);
      }
   }

   @SuppressWarnings("unchecked")
   private static <T> JsonElement write(ConfigOption<T> option, Object value) {
      return option.write((T) value);
   }

   private void load(Path source, boolean legacy) {
      try {
         if (!Files.isRegularFile(source) || Files.size(source) > 1_048_576) return;
         JsonElement root = JsonParser.parseString(Files.readString(source, StandardCharsets.UTF_8));
         if (!root.isJsonObject()) return;
         for (ConfigOption<?> option : ScreenshotViewerOptions.all()) {
            JsonElement json = legacy ? find(root.getAsJsonObject(), option.name()) : root.getAsJsonObject().get(option.name());
            Object parsed = json == null ? null : option.read(json);
            if (parsed != null) putRaw(option, parsed);
         }
      } catch (IOException | RuntimeException failure) {
         LOGGER.warn("Could not read the gallery settings from {}", source.getFileName(), failure);
      }
   }

   /** Option name lookup through the category objects of the standalone mod's file. */
   private static JsonElement find(JsonObject object, String name) {
      if (object.has(name)) return object.get(name);
      for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
         if (entry.getValue().isJsonObject()) {
            JsonElement nested = find(entry.getValue().getAsJsonObject(), name);
            if (nested != null) return nested;
         }
      }
      return null;
   }

   @SuppressWarnings("unchecked")
   private <T> void putRaw(ConfigOption<T> option, Object value) {
      put(option, (T) value);
   }
}
