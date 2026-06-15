package com.thelads.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class ConfigManager {
    private static volatile ModConfig config = new ModConfig();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    private static File getConfigFile() {
        try {
            FabricLoader loader = FabricLoader.getInstance();
            if (loader != null && loader.getConfigDir() != null) {
                return loader.getConfigDir().resolve("theladsclient.json").toFile();
            }
        } catch (Exception | Error e) {
            // Ignored, fallback to test directory below
        }
        return new File("config/theladsclient.json");
    }

    private static boolean isParseableAsBoolean(com.google.gson.JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) return false;
        com.google.gson.JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean()) return true;
        if (primitive.isString()) {
            String str = primitive.getAsString();
            return str.equalsIgnoreCase("true") || str.equalsIgnoreCase("false");
        }
        return false;
    }

    public static synchronized void loadConfig() {
        File configFile = getConfigFile();
        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                JsonObject jsonObject = GSON.fromJson(reader, JsonObject.class);
                if (jsonObject != null) {
                    ModConfig newConfig = new ModConfig();
                    boolean fullParseSuccess = false;
                    try {
                        boolean valid = true;
                        if (jsonObject.has("capesEnabled") && !isParseableAsBoolean(jsonObject.get("capesEnabled"))) {
                            valid = false;
                        }
                        if (jsonObject.has("uiScalingEnabled") && !isParseableAsBoolean(jsonObject.get("uiScalingEnabled"))) {
                            valid = false;
                        }
                        
                        if (valid) {
                            ModConfig parsed = GSON.fromJson(jsonObject, ModConfig.class);
                            if (parsed != null) {
                                newConfig = parsed;
                                fullParseSuccess = true;
                            }
                        }
                    } catch (Exception e) {
                        // ignore, fallback to partial parsing
                    }
                    if (!fullParseSuccess) {
                        if (jsonObject.has("capesEnabled") && isParseableAsBoolean(jsonObject.get("capesEnabled"))) {
                            try { newConfig.setCapesEnabled(jsonObject.get("capesEnabled").getAsBoolean()); } catch (Exception e) {}
                        }
                        if (jsonObject.has("uiScalingEnabled") && isParseableAsBoolean(jsonObject.get("uiScalingEnabled"))) {
                            try { newConfig.setUiScalingEnabled(jsonObject.get("uiScalingEnabled").getAsBoolean()); } catch (Exception e) {}
                        }
                    }
                    config = newConfig;
                } else {
                    config = new ModConfig();
                }
            } catch (Exception e) {
                config = new ModConfig();
            }
            if (config == null) {
                config = new ModConfig();
            }
        } else {
            config = new ModConfig();
        }
        save();
    }

    public static synchronized void load() {
        loadConfig();
    }

    public static synchronized void save() {
        File configFile = getConfigFile();
        if (configFile.getParentFile() != null) {
            configFile.getParentFile().mkdirs();
        }
        File tempFile = new File(configFile.getAbsolutePath() + ".tmp");
        try (FileWriter writer = new FileWriter(tempFile)) {
            GSON.toJson(config, writer);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        try {
            java.nio.file.Files.move(tempFile.toPath(), configFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            e.printStackTrace();
            // fallback
            if (configFile.exists()) configFile.delete();
            tempFile.renameTo(configFile);
        }
    }

    public static ModConfig getConfig() {
        if (config == null) {
            loadConfig();
        }
        return config;
    }
}
