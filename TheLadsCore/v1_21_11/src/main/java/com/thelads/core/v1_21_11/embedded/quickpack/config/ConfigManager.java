// Derived from quick-pack 1.4.0 by Drex (commit b80dac1, MIT); see META-INF/lads-sources/quickpack/LICENSE.
package com.thelads.core.v1_21_11.embedded.quickpack.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Same file as the original mod (config/quick-pack.json), so a player's setting carries over. */
public class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOGGER = LoggerFactory.getLogger("quick-pack");

    public static Config config = new Config();

    public static void load(Path configDirectory) {
        Path configFile = configDirectory.resolve("quick-pack.json");
        if (Files.exists(configFile)) {
            try {
                String data = Files.readString(configFile);
                try {
                    config = GSON.fromJson(data, Config.class);
                } catch (JsonSyntaxException e) {
                    LOGGER.error("Failed to parse quick-pack config", e);
                }
            } catch (IOException e) {
                LOGGER.error("Failed to load quick-pack config", e);
            }
        } else {
            try {
                Files.writeString(configFile, GSON.toJson(config));
            } catch (IOException e) {
                LOGGER.error("Failed to save quick-pack config", e);
            }
        }
    }
}
