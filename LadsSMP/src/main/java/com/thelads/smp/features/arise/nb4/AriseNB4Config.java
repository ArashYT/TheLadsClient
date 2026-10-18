package com.thelads.smp.features.arise.nb4;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Dedicated configuration manager for Native Batch NB4 features.
 * Persists to config/ladssmp/arise-nb4.json independently from main LadsConfig.
 */
public final class AriseNB4Config {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP/AriseNB4Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static volatile AriseNB4Config INSTANCE = new AriseNB4Config();

    /** Whether custom advancements in the ladssmp namespace are enabled. */
    public boolean advancementsEnabled = true;

    /** Multiplier applied to advancement experience reward payouts (default: 1.0). */
    public double advancementXpMultiplier = 1.0;

    /** Ticks to skip between heavy advancement checks (Arise default: 40). */
    public int skipTicksAdvancements = 40;

    /** Whether native Farmer's Delight addon culinary food & beverage items are enabled. */
    public boolean foodsEnabled = true;

    /** Whether melon juicing interaction with empty bottles is enabled (Arise default: true). */
    public boolean melonJuicing = true;

    /** Whether slicing foods drops directly to player inventory instead of in-world (Arise default: false). */
    public boolean giveSlicedDirectly = false;

    /** Whether butchery animal tallow drops from livestock are enabled. */
    public boolean butcheryTallowDrops = true;

    /** Chance for animal tallow to drop upon butchering livestock (0.0 to 1.0). */
    public double tallowDropChance = 0.25;

    public static AriseNB4Config get() {
        return INSTANCE;
    }

    public static Path getConfigPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("ladssmp").resolve("arise-nb4.json");
    }

    public static synchronized AriseNB4Config load() {
        Path path = getConfigPath();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                AriseNB4Config loaded = GSON.fromJson(reader, AriseNB4Config.class);
                if (loaded != null) {
                    INSTANCE = loaded;
                    LOGGER.info("[Arise NB4] Loaded configuration from {}", path);
                    return INSTANCE;
                }
            } catch (Exception e) {
                LOGGER.error("[Arise NB4] Failed to read {}, using defaults", path, e);
            }
        }
        INSTANCE = new AriseNB4Config();
        save(INSTANCE);
        return INSTANCE;
    }

    public static synchronized void save(AriseNB4Config config) {
        Path path = getConfigPath();
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
            LOGGER.info("[Arise NB4] Saved configuration to {}", path);
        } catch (IOException e) {
            LOGGER.error("[Arise NB4] Failed to save {}", path, e);
        }
    }
}
