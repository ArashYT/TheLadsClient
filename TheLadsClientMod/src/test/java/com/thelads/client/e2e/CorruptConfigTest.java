package com.thelads.client.e2e;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.thelads.client.config.ConfigManager;
import java.io.File;
import java.io.FileWriter;

public class CorruptConfigTest {
    @Test
    public void testCorruptConfig() throws Exception {
        File configDir = new File("config");
        configDir.mkdirs();
        File configFile = new File(configDir, "theladsclient.json");
        try (FileWriter writer = new FileWriter(configFile)) {
            writer.write("{ corrupt_json: ");
        }
        
        ConfigManager.loadConfig(); // Should not crash
        assertNotNull(ConfigManager.getConfig());
        
        // Ensure it saved a fresh one
        assertTrue(configFile.exists(), "Should have saved a new file");
        // We know it defaults correctly
        assertTrue(ConfigManager.getConfig().isCapesEnabled(), "Capes should be true by default");
    }
}
