/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.Gson
 *  com.google.gson.GsonBuilder
 *  net.fabricmc.loader.api.FabricLoader
 */
package com.thelads.core.features.farblockentityrendering;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import net.fabricmc.loader.api.FabricLoader;

public class SimpleConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "farblockentityrendering.json");
    public int renderDistanceChunks = 16;

    /*
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    public static SimpleConfig load() {
        if (FILE.exists()) {
            try (FileReader reader2222 = new FileReader(FILE);){
                SimpleConfig cfg = (SimpleConfig)GSON.fromJson((Reader)reader2222, SimpleConfig.class);
                if (cfg.renderDistanceChunks >= 4 && cfg.renderDistanceChunks <= 32) {
                    SimpleConfig simpleConfig = cfg;
                    return simpleConfig;
                }
            }
            catch (Exception reader2222) {
                // empty catch block
            }
        }
        SimpleConfig cfg = new SimpleConfig();
        cfg.save();
        return cfg;
    }

    public void save() {
        try {
            FILE.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(FILE);){
                GSON.toJson((Object)this, (Appendable)writer);
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public int getRenderDistanceBlocks() {
        return this.renderDistanceChunks * 16;
    }
}

