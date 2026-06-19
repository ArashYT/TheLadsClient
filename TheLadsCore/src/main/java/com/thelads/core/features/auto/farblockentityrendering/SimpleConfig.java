package com.thelads.core.features.auto.farblockentityrendering;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Recreation of FarBlockEntityRendering's SimpleConfig (by IlyRac).
 * Persists the block-entity render distance (in chunks) to
 * config/farblockentityrendering.json. Default 16 chunks, clamped 4..32.
 */
public class SimpleConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE =
            new File(FabricLoader.getInstance().getConfigDir().toFile(), "farblockentityrendering.json");

    public int renderDistanceChunks = 16;

    public static SimpleConfig load() {
        if (FILE.exists()) {
            try (FileReader reader = new FileReader(FILE)) {
                SimpleConfig cfg = GSON.fromJson(reader, SimpleConfig.class);
                if (cfg != null && cfg.renderDistanceChunks >= 4 && cfg.renderDistanceChunks <= 32) {
                    return cfg;
                }
            } catch (Exception ignored) {
                // fall through to defaults
            }
        }
        SimpleConfig cfg = new SimpleConfig();
        cfg.save();
        return cfg;
    }

    public void save() {
        try {
            FILE.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(FILE)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public int getRenderDistanceBlocks() {
        return this.renderDistanceChunks * 16;
    }
}
