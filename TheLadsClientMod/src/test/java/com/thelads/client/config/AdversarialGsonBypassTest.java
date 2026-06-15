package com.thelads.client.config;

import org.junit.jupiter.api.Test;
import java.io.File;
import java.io.FileWriter;

import static org.junit.jupiter.api.Assertions.*;

public class AdversarialGsonBypassTest {
    @Test
    public void testGsonBypassesDefaultValues() throws Exception {
        com.thelads.client.e2e.ReflectionHelper.setupMocks();
        
        File file = new File("config/theladsclient.json");
        file.getParentFile().mkdirs();
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("{\"uiScalingEnabled\": true}");
        }

        ConfigManager.loadConfig();
        
        // Because Gson uses UnsafeAllocator, capesEnabled might be incorrectly initialized to false!
        assertTrue(ConfigManager.getConfig().isCapesEnabled(), "Capes should still be enabled (the default)");
        assertTrue(ConfigManager.getConfig().isUiScalingEnabled(), "UI Scaling should be true");
        
        com.thelads.client.e2e.ReflectionHelper.teardownMocks();
    }
}
