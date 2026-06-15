package com.thelads.core.config;

import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ConfigStressTest {

    private File tempDir;
    private File configFile;

    @BeforeEach
    public void setUp() {
        tempDir = new File(System.getProperty("java.io.tmpdir"), "stress_test_config_" + System.currentTimeMillis());
        tempDir.mkdirs();
        configFile = new File(tempDir, "thelads_config.json");
        ConfigManager.setTestConfigFile(configFile);
    }

    @AfterEach
    public void tearDown() {
        configFile.delete();
        tempDir.delete();
        ConfigManager.setTestConfigFile(null);
    }

    @Test
    public void testWhitespaceFile() throws Exception {
        try (FileWriter w = new FileWriter(configFile)) {
            w.write("    \n\t  ");
        }
        assertDoesNotThrow(ConfigManager::load);
        assertDoesNotThrow(ConfigManager::save);
    }

    @Test
    public void testArrayInsteadOfObject() throws Exception {
        try (FileWriter w = new FileWriter(configFile)) {
            w.write("[1, 2, 3]");
        }
        assertDoesNotThrow(ConfigManager::load);
        // It should log an error but not crash.
        // It should also save successfully by overwriting.
        assertDoesNotThrow(ConfigManager::save);
        
        // Ensure it saved correctly as an object
        String content = new String(Files.readAllBytes(configFile.toPath()));
        assertTrue(content.contains("\"modules\""));
    }

    @Test
    public void testMalformedJsonSyntax() throws Exception {
        try (FileWriter w = new FileWriter(configFile)) {
            w.write("{ \"modules\": { \"TestModule\": { \"enabled\": true } "); // missing closing braces
        }
        assertDoesNotThrow(ConfigManager::load);
        assertDoesNotThrow(ConfigManager::save);
    }
}
