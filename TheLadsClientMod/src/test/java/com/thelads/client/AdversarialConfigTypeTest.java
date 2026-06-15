package com.thelads.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import com.thelads.client.config.ConfigManager;
import java.io.File;
import java.io.FileWriter;

public class AdversarialConfigTypeTest {

    private File getConfigFile() {
        return new File("config/theladsclient.json");
    }

    @BeforeEach
    public void setup() throws Exception {
        com.thelads.client.e2e.ReflectionHelper.setupMocks();
    }

    @AfterEach
    public void teardown() {
        com.thelads.client.e2e.ReflectionHelper.teardownMocks();
    }

    @Test
    public void testPartialInvalidFieldDoesNotWipeEntireConfig() throws Exception {
        File file = getConfigFile();
        file.getParentFile().mkdirs();
        try (FileWriter writer = new FileWriter(file)) {
            // capesEnabled is valid boolean, but uiScalingEnabled is an object (invalid for boolean)
            writer.write("{\"capesEnabled\": false, \"uiScalingEnabled\": {}}");
        }
        
        ConfigManager.loadConfig();
        
        // If the bug exists, an exception is thrown parsing uiScalingEnabled,
        // so the catch block runs and does config = new ModConfig().
        // Thus capesEnabled will revert to its default (true), even though we specified false!
        // A robust system should either preserve valid fields or not wipe the entire config.
        
        // This test ASSERTS that the valid field is preserved (i.e., should be false).
        // Since we know the bug exists, this test will FAIL, exposing the bug!
        assertFalse(ConfigManager.getConfig().isCapesEnabled(), "A single invalid field should not wipe the entire config, but it did!");
    }
}
