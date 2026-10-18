package com.thelads.smp.features.arise.nb2;

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
 * Configuration for Native Batch NB2 (config/ladssmp/arise-nb2.json).
 * Preserves default values from the original Arise SMP configuration.
 */
public final class NB2Config {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP/NB2Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static volatile NB2Config INSTANCE = new NB2Config();

    // 1. Distraction Free Recipes (Arise: distraction_free_recipes-client.toml)
    public boolean distractionFreeRecipes = false;

    // 3. HideName / No Render Name Tags
    public boolean hideName = true;

    // 4. Hitmarker
    public boolean hitmarker = true;
    public int hitmarkerTicks = 4;
    public boolean hitmarkerSound = true;

    // 5. Hold My Beer: Empty Hand (Arise: holdmybeer-client.toml)
    public boolean holdMyBeer = true;
    public boolean emptyOffhand = true;
    public boolean pressAndHold = false;
    public boolean showEmptyHandIcon = true;

    // 6. Not Enough Recipe Book (Arise: nerb.yaml)
    public boolean notEnoughRecipeBook = true;
    public String recipeBookMode = "TOGGLE";

    // 7. OWorld2Create
    public boolean oWorld2Create = true;

    // 8. Panoramica
    public boolean panoramica = true;

    // 9. Paxi (Arise: config/paxi)
    public boolean paxi = true;
    public String datapacksPath = "config/paxi/datapacks";
    public String resourcepacksPath = "config/paxi/resourcepacks";

    // 11. Quit Confirmation
    public boolean quitConfirm = true;

    // 12. Realistic Nametag (Arise: realisticnametag-client.toml)
    public boolean realisticNametag = true;
    public boolean realisticNametagSpectator = true;

    // 13. Remove Chat Bracket (Arise: removechatbracket-chat-format.json)
    public boolean removeChatBracket = true;
    public String chatFormat = "%s %s";

    // 14. Stonecutter Memory
    public boolean stonecutterMemory = true;

    // 15. Too Many Entities (Arise: too_many_entities.json)
    public boolean tooManyEntities = true;
    public int maxEntityCount = 64;

    // 16. Trade Uses
    public boolean tradeUses = true;

    // 17. YUNG's Menu Tweaks (Arise: yungsmenutweaks-forge-1_20_1.toml)
    public boolean yungsMenuTweaks = true;
    public boolean rightClickReversesCycleButtons = true;
    public boolean mouseScrollWheelAffectsSliders = true;

    public static NB2Config get() {
        return INSTANCE;
    }

    public static void setInstanceForTesting(NB2Config config) {
        INSTANCE = config;
    }

    public static synchronized NB2Config load() {
        Path path = getConfigPath();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                NB2Config loaded = GSON.fromJson(reader, NB2Config.class);
                if (loaded != null) {
                    INSTANCE = loaded;
                    return INSTANCE;
                }
            } catch (Exception e) {
                LOGGER.warn("[NB2] Failed to read config/ladssmp/arise-nb2.json, using defaults: {}", e.getMessage());
            }
        }
        INSTANCE = new NB2Config();
        INSTANCE.save();
        return INSTANCE;
    }

    public synchronized void save() {
        Path path = getConfigPath();
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            LOGGER.warn("[NB2] Failed to write config/ladssmp/arise-nb2.json: {}", e.getMessage());
        }
    }

    private static Path getConfigPath() {
        try {
            return FabricLoader.getInstance().getConfigDir().resolve("ladssmp").resolve("arise-nb2.json");
        } catch (Exception ignored) {
            return Path.of("config", "ladssmp", "arise-nb2.json");
        }
    }
}
