package com.thelads.smp.features.arise.nb10;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Configuration holder for Arise NB10 (Fairy Lights Hanging Decorations).
 * Persisted at config/ladssmp/arise-nb10.json.
 */
public final class AriseNB10Config {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP-NB10Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static AriseNB10Config INSTANCE = new AriseNB10Config();

    public boolean enabled = true;
    public int maxConnectionDistance = 32;
    public float sagMultiplier = 1.0F;
    public boolean windSway = true;
    public int renderDistance = 64;
    public int bulbLightEmission = 14;
    public boolean dropOnBreak = true;
    public boolean christmasJingles = true;
    public int jinglesAmplitude = 40;

    public static File getConfigFile() {
        try {
            return FabricLoader.getInstance().getConfigDir().resolve("ladssmp/arise-nb10.json").toFile();
        } catch (Throwable ignored) {
            return Path.of("build", "test-config", "ladssmp", "arise-nb10.json").toFile();
        }
    }

    public static AriseNB10Config get() {
        if (INSTANCE == null) {
            INSTANCE = new AriseNB10Config();
        }
        return INSTANCE;
    }

    public static void setInstanceForTesting(AriseNB10Config config) {
        INSTANCE = config;
    }

    public static AriseNB10Config load() {
        File file = getConfigFile();
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                AriseNB10Config cfg = GSON.fromJson(reader, AriseNB10Config.class);
                if (cfg != null) {
                    INSTANCE = cfg;
                    INSTANCE.validate();
                    return INSTANCE;
                }
            } catch (Exception e) {
                LOGGER.error("[LadsSMP-NB10] Failed to read config from {}, falling back to defaults", file, e);
            }
        }
        INSTANCE = new AriseNB10Config();
        save();
        return INSTANCE;
    }

    public static void save() {
        File file = getConfigFile();
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(INSTANCE, writer);
            }
        } catch (IOException e) {
            LOGGER.error("[LadsSMP-NB10] Failed to save config to {}", file, e);
        }
    }

    public void validate() {
        if (maxConnectionDistance < 4) maxConnectionDistance = 4;
        if (maxConnectionDistance > 64) maxConnectionDistance = 64;
        if (sagMultiplier < 0.1F) sagMultiplier = 0.1F;
        if (sagMultiplier > 3.0F) sagMultiplier = 3.0F;
        if (renderDistance < 16) renderDistance = 16;
        if (renderDistance > 256) renderDistance = 256;
        if (bulbLightEmission < 0) bulbLightEmission = 0;
        if (bulbLightEmission > 15) bulbLightEmission = 15;
        if (jinglesAmplitude < 1) jinglesAmplitude = 1;
    }
}
