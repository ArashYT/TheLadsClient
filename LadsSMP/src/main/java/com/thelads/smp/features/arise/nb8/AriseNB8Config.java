package com.thelads.smp.features.arise.nb8;

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
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for Batch NB8: Vein Mining (replaces FTB Ultimine from Arise SMP).
 * Persisted in config/ladssmp/arise-nb8.json with exact Arise SMP defaults.
 */
public final class AriseNB8Config {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP/NB8Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static volatile AriseNB8Config INSTANCE = new AriseNB8Config();

    /** Master switch for vein mining functionality. */
    public boolean enabled = true;

    /** Maximum number of blocks that can be mined in a single vein mining activation (Arise default: 64). */
    public int maxBlocks = 64;

    /** Maximum blocks processed per server tick to eliminate TPS lag spikes. */
    public int blocksPerTick = 16;

    /**
     * Hunger exhaustion multiplier per block mined with vein mining (Arise default: 20.0).
     * Vanilla base exhaustion for breaking a block is 0.005F. Exhaustion added = 0.005F * exhaustionMultiplier.
     */
    public double exhaustionMultiplier = 20.0;

    /**
     * Stop vein mining if tool durability reaches or drops below this value. 0 = allow breaking (Arise default: 0).
     */
    public int preventToolBreak = 0;

    /** Require a damageable tool or tool item in main hand to vein mine (Arise default: false). */
    public boolean requireTool = false;

    /** Maximum number of blocks to render wireframe outline for on client (Arise client default: 256). */
    public int renderOutline = 256;

    /** In shaped modes (tunnels), allow mining any harvestable block in the shape path (Arise default: ["*"]). */
    public boolean shapedAnyBlock = true;

    /** Safety toggle: whether blocks containing tile/block entity inventories (chests, barrels) can be vein-mined. */
    public boolean mineContainers = false;

    /** Tag patterns considered identical blocks in shapeless mode (Arise default tags). */
    public List<String> mergeTags = new ArrayList<>(List.of(
            "#minecraft:base_stone_overworld",
            "c:*_ores",
            "forge:ores/*",
            "#c:ores",
            "#minecraft:logs"
    ));

    public static AriseNB8Config get() {
        return INSTANCE;
    }

    public static AriseNB8Config load() {
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve("ladssmp");
        Path configFile = configDir.resolve("arise-nb8.json");

        if (Files.exists(configFile)) {
            try (Reader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
                AriseNB8Config loaded = GSON.fromJson(reader, AriseNB8Config.class);
                if (loaded != null) {
                    INSTANCE = loaded;
                    return loaded;
                }
            } catch (Exception e) {
                LOGGER.error("[Arise NB8] Failed to read config from {}, falling back to defaults: {}", configFile, e.getMessage());
            }
        }

        INSTANCE = new AriseNB8Config();
        save(INSTANCE, configFile);
        return INSTANCE;
    }

    public static void save(AriseNB8Config config, Path path) {
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            LOGGER.error("[Arise NB8] Failed to write config to {}: {}", path, e.getMessage());
        }
    }
}
