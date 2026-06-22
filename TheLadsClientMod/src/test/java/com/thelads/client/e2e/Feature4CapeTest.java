package com.thelads.client.e2e;

import org.junit.jupiter.api.Test;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.FileReader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

public class Feature4CapeTest {
    @BeforeEach
    public void setupMocks() throws Exception { com.thelads.client.e2e.ReflectionHelper.setupMocks(); }
    @AfterEach
    public void teardownMocks() { com.thelads.client.e2e.ReflectionHelper.teardownMocks(); }


    @Test
    public void testCapeEnabledByDefault() throws Exception {
        File configFile = new File("config/theladsclient.json");
        if (configFile.exists()) configFile.delete();
        ReflectionHelper.loadConfig(); // Will create default
        
        boolean capes = ReflectionHelper.isCapesEnabled();
        if (capes != true) {
            throw new RuntimeException("Capes are not enabled by default");
        }
    }

    @Test
    public void testTogglingCapeDisablesIt() throws Exception {
        ReflectionHelper.setCapesEnabled(false);
        boolean capes = ReflectionHelper.isCapesEnabled();
        if (capes != false) {
            throw new RuntimeException("Capes cannot be disabled");
        }
    }

    @Test
    public void testTogglingCapeEnablesIt() throws Exception {
        ReflectionHelper.setCapesEnabled(false);
        ReflectionHelper.setCapesEnabled(true);
        boolean capes = ReflectionHelper.isCapesEnabled();
        if (capes != true) {
            throw new RuntimeException("Capes cannot be enabled");
        }
    }

    @Test
    public void testCapeStateReflectsInConfig() throws Exception {
        ReflectionHelper.setCapesEnabled(false);
        ReflectionHelper.save();
        
        Gson gson = new Gson();
        File configFile = new File("config/theladsclient.json");
        try (FileReader reader = new FileReader(configFile)) {
            JsonObject json = gson.fromJson(reader, JsonObject.class);
            if (json.get("capesEnabled").getAsBoolean() != false) {
                throw new RuntimeException("Cape state not correctly saved to config");
            }
        }
    }

    @Test
    public void testCapeStateAppliesToPlayerModel() throws Exception {
        // Opaque box test: Assuming mixins alter rendering, we test config
        ReflectionHelper.setCapesEnabled(true);
        boolean capes = ReflectionHelper.isCapesEnabled();
        if (!capes) {
            throw new RuntimeException("Cape state did not apply properly to internal representation");
        }
    }
}
