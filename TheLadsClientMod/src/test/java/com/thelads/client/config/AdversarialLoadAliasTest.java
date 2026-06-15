package com.thelads.client.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class AdversarialLoadAliasTest {

    @Test
    public void testLoadIsAliasForLoadConfig() throws Exception {
        com.thelads.client.e2e.ReflectionHelper.setupMocks();
        
        // Change config dynamically
        ConfigManager.getConfig().setCapesEnabled(false);
        ConfigManager.save();
        
        // Reset to true in memory
        ConfigManager.getConfig().setCapesEnabled(true);
        
        // Call load() which should reload from disk (where it's false)
        ConfigManager.load();
        
        assertFalse(ConfigManager.getConfig().isCapesEnabled());
        
        com.thelads.client.e2e.ReflectionHelper.teardownMocks();
    }
}
