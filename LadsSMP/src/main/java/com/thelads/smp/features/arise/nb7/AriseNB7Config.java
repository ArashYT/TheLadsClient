package com.thelads.smp.features.arise.nb7;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Configuration for Arise SMP Batch NB7 (Creeper Overhaul Biome Creepers).
 * Preserves the exact configuration keys and defaults from Arise SMP's creeperoverhaul.jsonc.
 */
public final class AriseNB7Config {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP-NB7");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path getConfigPath() {
        try {
            return FabricLoader.getInstance().getConfigDir().resolve("ladssmp/arise-nb7.json");
        } catch (Throwable ignored) {
            return Path.of("config", "ladssmp", "arise-nb7.json");
        }
    }

    private static AriseNB7Config INSTANCE = new AriseNB7Config();

    public boolean destroyBlocks = true;
    public boolean replaceDefaultCreeper = true;
    public boolean allowSpawning = true;

    public boolean allowJungleCreeperSpawning = true;
    public boolean allowBambooCreeperSpawning = true;
    public boolean allowDesertCreeperSpawning = true;
    public boolean allowBadlandsCreeperSpawning = true;
    public boolean allowHillsCreeperSpawning = true;
    public boolean allowSavannahCreeperSpawning = true;
    public boolean allowMushroomCreeperSpawning = true;
    public boolean allowSwampCreeperSpawning = true;
    public boolean allowDripstoneCreeperSpawning = true;
    public boolean allowCaveCreeperSpawning = true;
    public boolean allowDarkOakCreeperSpawning = true;
    public boolean allowSpruceCreeperSpawning = true;
    public boolean allowBeachCreeperSpawning = true;
    public boolean allowSnowyCreeperSpawning = true;
    public boolean allowOceanCreeperSpawning = true;

    public static AriseNB7Config get() {
        return INSTANCE;
    }

    public static void setInstance(AriseNB7Config config) {
        INSTANCE = config;
    }

    public static void load() {
        Path path = getConfigPath();
        try {
            if (Files.exists(path)) {
                try (Reader reader = Files.newBufferedReader(path)) {
                    AriseNB7Config loaded = GSON.fromJson(reader, AriseNB7Config.class);
                    if (loaded != null) {
                        INSTANCE = loaded;
                        LOGGER.info("[LadsSMP-NB7] Loaded config from {}", path);
                        return;
                    }
                }
            }
            save();
        } catch (Exception e) {
            LOGGER.error("[LadsSMP-NB7] Failed to load config, saving defaults", e);
            save();
        }
    }

    public static void save() {
        Path path = getConfigPath();
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(INSTANCE, writer);
            }
        } catch (Exception e) {
            LOGGER.error("[LadsSMP-NB7] Failed to save config to {}", path, e);
        }
    }

    public boolean isVariantEnabled(BiomeCreeperVariant variant) {
        if (!allowSpawning) return false;
        if (variant == null) return false;
        return switch (variant) {
            case JUNGLE -> allowJungleCreeperSpawning;
            case BAMBOO -> allowBambooCreeperSpawning;
            case DESERT -> allowDesertCreeperSpawning;
            case BADLANDS -> allowBadlandsCreeperSpawning;
            case HILLS -> allowHillsCreeperSpawning;
            case SAVANNAH -> allowSavannahCreeperSpawning;
            case MUSHROOM -> allowMushroomCreeperSpawning;
            case SWAMP -> allowSwampCreeperSpawning;
            case DRIPSTONE -> allowDripstoneCreeperSpawning;
            case CAVE -> allowCaveCreeperSpawning;
            case DARK_OAK -> allowDarkOakCreeperSpawning;
            case SPRUCE -> allowSpruceCreeperSpawning;
            case BEACH -> allowBeachCreeperSpawning;
            case SNOWY -> allowSnowyCreeperSpawning;
            case OCEAN -> allowOceanCreeperSpawning;
        };
    }
}
