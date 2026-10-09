package com.thelads.core.v1_20_1.hud.jade;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.thelads.core.v1_20_1.hud.Hud1201;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** Settings of the Lads Jade overlay, kept in {@code <game dir>/config/theladscore-jade.json}. */
public final class JadeConfig1201 {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static JadeConfig1201 instance;

    // Info lines
    public boolean icon = true;
    public boolean modName = true;
    public boolean health = true;
    public boolean armor = true;
    public boolean growth = true;
    public boolean containers = true;
    public boolean redstone = true;
    public boolean blockStates = true;
    public boolean spawner = true;
    public boolean signs = true;
    public boolean banners = true;
    public boolean harvest = true;
    public boolean animals = true;
    // Behaviour
    public boolean displayFluids = false;
    public boolean onlySneaking = false;
    public boolean hideCreative = false;
    public boolean hideWithDebug = true;
    // Look: anchor is a 3x3 grid cell (row * 3 + column), 1 = top centre.
    public int anchor = 1;
    public int offsetX = 0;
    public int offsetY = 0;
    public float scale = 1.0f;
    public float opacity = 0.8f;

    public static JadeConfig1201 get() {
        if (instance == null) instance = load();
        return instance;
    }

    static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("theladscore-jade.json");
    }

    private static JadeConfig1201 load() {
        Path file = file();
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JadeConfig1201 loaded = GSON.fromJson(reader, JadeConfig1201.class);
                if (loaded != null) return loaded.clamp();
            } catch (IOException | RuntimeException e) {
                Hud1201.LOG.warn("Jade: could not read {}, using defaults", file, e);
            }
        }
        return new JadeConfig1201();
    }

    public void save() {
        clamp();
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            Hud1201.LOG.warn("Jade: could not save {}", file, e);
        }
    }

    private JadeConfig1201 clamp() {
        anchor = Math.max(0, Math.min(8, anchor));
        scale = Math.max(0.5f, Math.min(2.0f, scale));
        opacity = Math.max(0f, Math.min(1f, opacity));
        offsetX = Math.max(-400, Math.min(400, offsetX));
        offsetY = Math.max(-400, Math.min(400, offsetY));
        return this;
    }
}
