package com.thelads.smp.features.arise.nb6;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Configuration for Native Batch NB6 (Tide-style Fishing Overhaul).
 * Stored at config/ladssmp/arise-nb6.json.
 */
public final class AriseNB6Config {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP/AriseNB6Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static File getDefaultConfigFile() {
        try {
            return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("ladssmp").resolve("arise-nb6.json").toFile();
        } catch (Throwable ignored) {
            return new File("build/test-config/ladssmp/arise-nb6.json");
        }
    }
    private static final File CONFIG_FILE = getDefaultConfigFile();

    private static AriseNB6Config INSTANCE = new AriseNB6Config();

    public boolean enabled = true;
    public boolean holdToCast = true;
    public float maxCastChargeSeconds = 1.5f;
    public float minCastPower = 0.4f;
    public float maxCastPower = 1.6f;
    public boolean doMinigame = true;
    public float minigameDifficulty = 1.2f;
    public int minigameDurationTicks = 100;
    public int crateWeight = 6;
    public boolean giveJournal = true;
    public boolean showToasts = true;
    public boolean customFishLoot = true;
    public boolean lavaFishing = true;

    public static AriseNB6Config get() {
        return INSTANCE;
    }

    public static synchronized AriseNB6Config load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                AriseNB6Config loaded = GSON.fromJson(reader, AriseNB6Config.class);
                if (loaded != null) {
                    INSTANCE = loaded;
                    LOGGER.info("[Arise NB6] Loaded fishing config: enabled={}, holdToCast={}, doMinigame={}, diff={}",
                            INSTANCE.enabled, INSTANCE.holdToCast, INSTANCE.doMinigame, INSTANCE.minigameDifficulty);
                    return INSTANCE;
                }
            } catch (Exception e) {
                LOGGER.error("[Arise NB6] Failed to load config from {}. Keeping active configuration.", CONFIG_FILE.getPath(), e);
                try {
                    Path path = CONFIG_FILE.toPath();
                    Path corrupt = path.resolveSibling(path.getFileName().toString() + ".corrupt-" + System.currentTimeMillis());
                    Files.copy(path, corrupt, StandardCopyOption.REPLACE_EXISTING);
                    LOGGER.warn("Corrupt Arise NB6 config backed up to {}", corrupt);
                } catch (Exception ignored) {}
                if (INSTANCE != null) return INSTANCE;
            }
        }
        INSTANCE = new AriseNB6Config();
        save(INSTANCE);
        return INSTANCE;
    }

    public static synchronized void save(AriseNB6Config config) {
        INSTANCE = config;
        Path path = CONFIG_FILE.toPath();
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Path temp = path.resolveSibling(path.getFileName().toString() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temp)) {
                GSON.toJson(config, writer);
            }
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.error("[Arise NB6] Failed to save config to {}", CONFIG_FILE.getPath(), e);
        }
    }

    public static synchronized void reload() {
        load();
    }
}
