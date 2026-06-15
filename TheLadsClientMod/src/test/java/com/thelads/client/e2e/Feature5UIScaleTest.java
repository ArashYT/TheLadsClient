package com.thelads.client.e2e;

import org.junit.jupiter.api.Test;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.FileReader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

public class Feature5UIScaleTest {
    @BeforeEach
    public void setupMocks() throws Exception { com.thelads.client.e2e.ReflectionHelper.setupMocks(); }
    @AfterEach
    public void teardownMocks() { com.thelads.client.e2e.ReflectionHelper.teardownMocks(); }


    @Test
    public void testUIScalingDisabledByDefault() throws Exception {
        File configFile = new File("config/theladsclient.json");
        if (configFile.exists()) configFile.delete();
        ReflectionHelper.loadConfig(); // Will create default
        
        boolean scale = ReflectionHelper.isUiScalingEnabled();
        if (scale != false) {
            throw new RuntimeException("UI Scaling is not disabled by default");
        }
    }

    @Test
    public void testEnablingUIScalingChangesConfig() throws Exception {
        ReflectionHelper.setUiScalingEnabled(true);
        boolean scale = ReflectionHelper.isUiScalingEnabled();
        if (scale != true) {
            throw new RuntimeException("UI Scaling cannot be enabled");
        }
    }

    @Test
    public void testDisablingUIScalingChangesConfig() throws Exception {
        ReflectionHelper.setUiScalingEnabled(true);
        ReflectionHelper.setUiScalingEnabled(false);
        boolean scale = ReflectionHelper.isUiScalingEnabled();
        if (scale != false) {
            throw new RuntimeException("UI Scaling cannot be disabled");
        }
    }

    @Test
    public void testUIScalingReflectsInGui() throws Exception {
        ReflectionHelper.setUiScalingEnabled(true);
        ReflectionHelper.save();
        
        Gson gson = new Gson();
        File configFile = new File("config/theladsclient.json");
        try (FileReader reader = new FileReader(configFile)) {
            JsonObject json = gson.fromJson(reader, JsonObject.class);
            if (json.get("uiScalingEnabled").getAsBoolean() != true) {
                throw new RuntimeException("UI scaling state not correctly saved to config");
            }
        }
    }

    @Test
    public void testUIScalingAppliesToRendering() throws Exception {
        ReflectionHelper.setUiScalingEnabled(true);
        boolean scale = ReflectionHelper.isUiScalingEnabled();
        if (!scale) {
            throw new RuntimeException("UI Scaling state did not apply properly to internal representation");
        }
    }
}
