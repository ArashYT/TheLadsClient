package com.thelads.core.config;

import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class ConfigManagerTest {

    private File tempDir;
    private File configFile;

    @BeforeEach
    public void setUp() {
        tempDir = new File(System.getProperty("java.io.tmpdir"), "thelads_test_config_" + System.currentTimeMillis());
        tempDir.mkdirs();
        configFile = new File(tempDir, "thelads_config.json");
        ConfigManager.setTestConfigFile(configFile);
    }

    @AfterEach
    public void tearDown() throws Exception {
        configFile.delete();
        tempDir.delete();
        ConfigManager.setTestConfigFile(null);
    }

    @Test
    public void testEmptyJSONFileDoesNotCrash() throws Exception {
        try (FileWriter w = new FileWriter(configFile)) {
            w.write("");
        }
        assertDoesNotThrow(ConfigManager::load);
    }

    @Test
    public void testMalformedModuleConfigDoesNotAbortRest() throws Exception {
        try (FileWriter w = new FileWriter(configFile)) {
            // TestModule has malformed config (array), but another module (if it existed) or just the rest of the parsing would continue
            w.write("{ \"modules\": { \"MalformedModule\": [], \"TestModule\": { \"enabled\": true } } }");
        }
        
        // Ensure TestModule starts as false
        Module testModule = ModuleManager.getInstance().getModule("TestModule");
        if (testModule != null) {
            testModule.setEnabled(false);
        }
        
        ConfigManager.load();
        
        if (testModule != null) {
            assertTrue(testModule.isEnabled());
        }
    }
}
