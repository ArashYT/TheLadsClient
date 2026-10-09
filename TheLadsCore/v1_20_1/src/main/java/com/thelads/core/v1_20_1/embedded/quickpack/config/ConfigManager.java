// Derived from quick-pack 1.4.0 by Drex (commit b80dac1, MIT); see META-INF/lads-sources/quickpack/LICENSE.
// Modified by The Lads: ported to Minecraft 1.20.1 from the Lads 26.3 embedded copy.
package com.thelads.core.v1_20_1.embedded.quickpack.config;

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
                    Config parsed = GSON.fromJson(data, Config.class);
                    if (parsed != null) config = parsed;
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
