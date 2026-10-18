package com.thelads.smp.features.arise.nb9;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for Arise SMP Batch NB9 (Amendments-style Vanilla Block Upgrades).
 * Preserves the exact configuration keys and defaults from Arise SMP's amendments-common.toml and amendments-client.toml.
 */
public final class AriseNB9Config {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP-NB9");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path getConfigPath() {
        try {
            return FabricLoader.getInstance().getConfigDir().resolve("ladssmp/arise-nb9.json");
        } catch (Throwable ignored) {
            return Path.of("config", "ladssmp", "arise-nb9.json");
        }
    }

    private static AriseNB9Config INSTANCE = new AriseNB9Config();

    // Cauldron
    public boolean enhancedCauldron = true;
    public boolean dyeWater = true;
    public int dyeRecipesPerLayer = 4;
    public int potionRecipesPerLayer = 2;
    public String potionsMixing = "ON"; // OFF, ONLY_BOILING, ON
    public int potionMixingLimit = 8;

    // Lantern
    public boolean wallLanterns = true;

    // Hanging Signs
    public boolean itemsOnSigns = true;

    // Lectern
    public boolean lecternBookTitle = true;

    // Mob Head / Skulls
    public boolean skullPiles = true;
    public boolean skullCandles = true;

    // Carpets
    public boolean carpetedStairs = true;
    public boolean carpetedSlabs = true;

    // Cake
    public boolean doubleCake = true;

    // Bell
    public boolean chainRinging = true;
    public int chainLength = 16;

    // Tripwire Hook
    public boolean toolHook = true;

    // Flower Pot
    public boolean hangingPot = true;

    // Torch
    public boolean torchFire = true;
    public int torchFireDurationSeconds = 2;

    // Lily Pad
    public boolean betterLilypads = true;

    // Inverse Potions Map (used for potion mixing cancellation)
    public Map<String, String> inversePotions = new HashMap<>();

    public AriseNB9Config() {
        initDefaultInversePotions();
    }

    private void initDefaultInversePotions() {
        inversePotions.put("minecraft:slowness", "minecraft:speed");
        inversePotions.put("minecraft:luck", "minecraft:unluck");
        inversePotions.put("minecraft:haste", "minecraft:mining_fatigue");
        inversePotions.put("minecraft:mining_fatigue", "minecraft:haste");
        inversePotions.put("minecraft:instant_health", "minecraft:instant_damage");
        inversePotions.put("minecraft:unluck", "minecraft:luck");
        inversePotions.put("minecraft:speed", "minecraft:slowness");
        inversePotions.put("minecraft:strength", "minecraft:weakness");
        inversePotions.put("minecraft:weakness", "minecraft:strength");
        inversePotions.put("minecraft:instant_damage", "minecraft:instant_health");
    }

    public static AriseNB9Config get() {
        return INSTANCE;
    }

    public static void setInstance(AriseNB9Config config) {
        INSTANCE = config;
    }

    public static void load() {
        Path path = getConfigPath();
        try {
            if (Files.exists(path)) {
                try (Reader reader = Files.newBufferedReader(path)) {
                    AriseNB9Config loaded = GSON.fromJson(reader, AriseNB9Config.class);
                    if (loaded != null) {
                        INSTANCE = loaded;
                        LOGGER.info("[LadsSMP-NB9] Loaded config from {}", path);
                        return;
                    }
                }
            }
            save();
        } catch (Exception e) {
            LOGGER.error("[LadsSMP-NB9] Failed to load config, saving defaults", e);
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
            LOGGER.error("[LadsSMP-NB9] Failed to save config to {}", path, e);
        }
    }
}
