package com.thelads.client.e2e;

import org.junit.jupiter.api.Test;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

public class Feature3ConfigTest {
    @BeforeEach
    public void setupMocks() throws Exception { com.thelads.client.e2e.ReflectionHelper.setupMocks(); }
    @AfterEach
    public void teardownMocks() { com.thelads.client.e2e.ReflectionHelper.teardownMocks(); }


    @Test
    public void testConfigSavesToCorrectPath() throws Exception {
        ReflectionHelper.save();
        File configFile = new File("config/theladsclient.json");
        if (!configFile.exists()) {
            throw new RuntimeException("Config file does not exist at correct path");
        }
    }

    @Test
    public void testConfigLoadsValidJson() throws Exception {
        File configFile = new File("config/theladsclient.json");
        configFile.getParentFile().mkdirs();
        try (FileWriter writer = new FileWriter(configFile)) {
            writer.write("{\"capesEnabled\":false,\"uiScalingEnabled\":true}");
        }
        
        ReflectionHelper.loadConfig();
        boolean capes = ReflectionHelper.isCapesEnabled();
        boolean scale = ReflectionHelper.isUiScalingEnabled();
        
        if (capes != false || scale != true) {
            throw new RuntimeException("Config failed to load actual values");
        }
    }

    @Test
    public void testConfigHandlesMalformedJson() throws Exception {
        File configFile = new File("config/theladsclient.json");
        configFile.getParentFile().mkdirs();
        try (FileWriter writer = new FileWriter(configFile)) {
            writer.write("{malformed json}");
        }
        
        // Should not throw, should revert to defaults
        ReflectionHelper.loadConfig();
        boolean capes = ReflectionHelper.isCapesEnabled();
        if (capes != true) { // assuming default is true
            throw new RuntimeException("Config did not handle malformed JSON safely");
        }
    }

    @Test
    public void testConfigUpdatesMissingFields() throws Exception {
        File configFile = new File("config/theladsclient.json");
        configFile.getParentFile().mkdirs();
        try (FileWriter writer = new FileWriter(configFile)) {
            writer.write("{\"capesEnabled\":false}"); // missing uiScalingEnabled
        }
        
        ReflectionHelper.loadConfig();
        ReflectionHelper.save();
        
        Gson gson = new Gson();
        try (FileReader reader = new FileReader(configFile)) {
            JsonObject json = gson.fromJson(reader, JsonObject.class);
            if (!json.has("uiScalingEnabled") || !json.get("uiScalingEnabled").isJsonPrimitive()) {
                throw new RuntimeException("Config did not append missing fields");
            }
        }
    }

    @Test
    public void testConfigPersistsBetweenSessions() throws Exception {
        ReflectionHelper.setCapesEnabled(false);
        ReflectionHelper.save();
        
        // Simulate restart
        ReflectionHelper.loadConfig();
        
        boolean capes = ReflectionHelper.isCapesEnabled();
        if (capes != false) {
            throw new RuntimeException("Config state did not persist");
        }
    }
}
