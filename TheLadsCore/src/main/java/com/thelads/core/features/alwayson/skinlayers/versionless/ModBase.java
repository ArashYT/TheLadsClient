package com.thelads.core.features.alwayson.skinlayers.versionless;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.thelads.core.features.alwayson.skinlayers.versionless.config.Config;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class ModBase {
    public static final Logger LOGGER = LogManager.getLogger();
    public static Config config = null;
    private File settingsFile = new File("config", "skinlayers.json");
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    public static boolean disguiseHeadsCompatibility = false;

    public void onInitialize() {
        if (this.settingsFile.exists()) {
            try {
                config = new Gson().fromJson(new String(Files.readAllBytes(this.settingsFile.toPath()), StandardCharsets.UTF_8), Config.class);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        if (config == null) {
            config = new Config();
            this.writeConfig();
        }
        try {
            Class<?> clientClass = Class.forName("dev.tr7zw.disguiseheads.DisguiseHeadsShared");
            disguiseHeadsCompatibility = clientClass != null;
            LOGGER.info("Found DisguiseHeads, enable compatibility!");
        } catch (Throwable throwable) {
            // empty catch block
        }
    }

    public void writeConfig() {
        if (this.settingsFile.exists()) {
            this.settingsFile.delete();
        }
        try {
            Files.write(this.settingsFile.toPath(), this.gson.toJson(config).getBytes(StandardCharsets.UTF_8), new OpenOption[0]);
        } catch (IOException e1) {
            e1.printStackTrace();
        }
    }
}
