package com.thelads.client.e2e;

import org.junit.jupiter.api.Test;
import java.io.File;
import java.io.FileWriter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

public class Tier2BoundaryTest {
    @BeforeEach
    public void setupMocks() throws Exception { com.thelads.client.e2e.ReflectionHelper.setupMocks(); }
    @AfterEach
    public void teardownMocks() { com.thelads.client.e2e.ReflectionHelper.teardownMocks(); }


    @Test
    public void testEmptyConfigFile() throws Exception {
        File configFile = new File("config/theladsclient.json");
        configFile.getParentFile().mkdirs();
        try (FileWriter writer = new FileWriter(configFile)) {
            writer.write("");
        }
        ReflectionHelper.loadConfig();
        if (!ReflectionHelper.isCapesEnabled() || ReflectionHelper.isUiScalingEnabled()) {
            throw new RuntimeException("Did not recover gracefully from empty config");
        }
    }

    @Test
    public void testConfigWithExtraFields() throws Exception {
        File configFile = new File("config/theladsclient.json");
        configFile.getParentFile().mkdirs();
        try (FileWriter writer = new FileWriter(configFile)) {
            writer.write("{\"capesEnabled\":false,\"unknownField\":\"test\"}");
        }
        ReflectionHelper.loadConfig();
        if (ReflectionHelper.isCapesEnabled()) {
            throw new RuntimeException("Did not read valid field when extra fields present");
        }
    }

    @Test
    public void testConfigWithMissingFields() throws Exception {
        File configFile = new File("config/theladsclient.json");
        configFile.getParentFile().mkdirs();
        try (FileWriter writer = new FileWriter(configFile)) {
            writer.write("{}");
        }
        ReflectionHelper.loadConfig();
        if (!ReflectionHelper.isCapesEnabled() || ReflectionHelper.isUiScalingEnabled()) {
            throw new RuntimeException("Did not use defaults for missing fields");
        }
    }

    @Test
    public void testRapidKeyPresses() throws Exception {
        for (int i = 0; i < 50; i++) {
            ReflectionHelper.simulateRightShiftKeyPress();
        }
        // Assuming no crash is a success
        Object currentScreen = ReflectionHelper.getCurrentScreen();
        if (currentScreen == null) {
            throw new RuntimeException("GUI did not stabilize after rapid presses");
        }
    }

    @Test
    public void testExtremeScaleValues() throws Exception {
        // Since we only have boolean for now, test boolean reflection boundary
        ReflectionHelper.setUiScalingEnabled(true);
        ReflectionHelper.setUiScalingEnabled(false);
        ReflectionHelper.setUiScalingEnabled(true);
        if (!ReflectionHelper.isUiScalingEnabled()) {
            throw new RuntimeException("Boolean boundary toggling failed");
        }
    }
}
