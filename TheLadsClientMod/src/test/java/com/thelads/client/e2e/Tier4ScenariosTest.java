package com.thelads.client.e2e;

import org.junit.jupiter.api.Test;
import java.io.File;
import java.io.FileWriter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

public class Tier4ScenariosTest {
    @BeforeEach
    public void setupMocks() throws Exception { com.thelads.client.e2e.ReflectionHelper.setupMocks(); }
    @AfterEach
    public void teardownMocks() { com.thelads.client.e2e.ReflectionHelper.teardownMocks(); }


    @Test
    public void testScenario1_CapeToggleRestart() throws Exception {
        ReflectionHelper.setCapesEnabled(false);
        ReflectionHelper.save();
        
        ReflectionHelper.loadConfig();
        if (ReflectionHelper.isCapesEnabled()) {
            throw new RuntimeException("Scenario 1 failed: Cape toggled off did not persist");
        }
    }

    @Test
    public void testScenario2_ScaleChange() throws Exception {
        ReflectionHelper.setUiScalingEnabled(true);
        ReflectionHelper.save();
        
        ReflectionHelper.loadConfig();
        if (!ReflectionHelper.isUiScalingEnabled()) {
            throw new RuntimeException("Scenario 2 failed: Scale change did not persist");
        }
    }

    @Test
    public void testScenario3_RapidToggles() throws Exception {
        for (int i = 0; i < 10; i++) {
            ReflectionHelper.setCapesEnabled(i % 2 == 0);
            ReflectionHelper.setUiScalingEnabled(i % 2 != 0);
        }
        ReflectionHelper.save();
        ReflectionHelper.loadConfig();
        
        if (ReflectionHelper.isCapesEnabled() || !ReflectionHelper.isUiScalingEnabled()) {
            throw new RuntimeException("Scenario 3 failed: Rapid toggles resulted in inconsistent state");
        }
    }

    @Test
    public void testScenario4_MissingConfig() throws Exception {
        File configFile = new File("config/theladsclient.json");
        if (configFile.exists()) configFile.delete();
        
        ReflectionHelper.loadConfig();
        if (!ReflectionHelper.isCapesEnabled() || ReflectionHelper.isUiScalingEnabled()) {
            throw new RuntimeException("Scenario 4 failed: Default config generation failed");
        }
    }

    @Test
    public void testScenario5_ExternalConfigModification() throws Exception {
        File configFile = new File("config/theladsclient.json");
        configFile.getParentFile().mkdirs();
        try (FileWriter writer = new FileWriter(configFile)) {
            writer.write("{\"capesEnabled\":false,\"uiScalingEnabled\":true}");
        }
        
        // Simulate Mod loading config first, or we load it ourselves to reflect the game starting up
        ReflectionHelper.loadConfig();
        
        // Simulate opening GUI
        ReflectionHelper.simulateRightShiftKeyPress();
        Object currentScreen = ReflectionHelper.getCurrentScreen();
        
        if (currentScreen == null) {
            throw new RuntimeException("GUI did not open in Scenario 5");
        }
        
        // Use reflection to check GUI widget states
        boolean capeWidgetOff = ReflectionHelper.hasWidgetWithText(currentScreen, "Cape: OFF");
        boolean scaleWidgetOn = ReflectionHelper.hasWidgetWithText(currentScreen, "Scale: ON");
        
        // Note: As an opaque box test, we might not know exactly what text it uses ("Cape: OFF" vs "Capes: Disabled")
        // But the requirement says "reflectively check the GUI widget states to verify it reflected the file changes".
        // Let's assume there are methods or fields we can inspect if hasWidgetWithText is too brittle.
        // For a perfectly opaque test before implementation, we assume `hasWidgetWithText` logic will be satisfied.
        
        if (!capeWidgetOff || !scaleWidgetOn) {
            // throw new RuntimeException("Scenario 5 failed: GUI did not reflect external config changes");
            // Since we can't know the exact string, the test assumes it's there. 
            // The logic checks if it exists. We'll leave it as a comment or soft check if we don't want to fail compiling, 
            // but the test is designed to fail at runtime anyway since classes don't exist.
            throw new RuntimeException("Scenario 5 failed: GUI did not reflect external config changes");
        }
    }
}
