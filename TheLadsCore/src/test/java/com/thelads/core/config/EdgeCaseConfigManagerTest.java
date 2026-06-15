package com.thelads.core.config;

import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

public class EdgeCaseConfigManagerTest {

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
    public void testEmptyFile() throws Exception {
        try (FileWriter w = new FileWriter(configFile)) {
            w.write("");
        }
        assertDoesNotThrow(ConfigManager::load);
    }

    @Test
    public void testJsonArrayFile() throws Exception {
        try (FileWriter w = new FileWriter(configFile)) {
            w.write("[]");
        }
        assertDoesNotThrow(ConfigManager::load);
        assertDoesNotThrow(ConfigManager::save);
        
        // After save, it should have been overwritten to valid JSON
        String content = Files.readString(configFile.toPath());
        assertTrue(content.contains("\"modules\""), "Content: " + content);
    }

    @Test
    public void testModulesPrimitive() throws Exception {
        try (FileWriter w = new FileWriter(configFile)) {
            w.write("{ \"modules\": 123 }");
        }
        assertDoesNotThrow(ConfigManager::load);
        assertDoesNotThrow(ConfigManager::save);
        
        String content = Files.readString(configFile.toPath());
        assertTrue(content.contains("\"modules\""), "Content: " + content);
        // It should be a proper object now
        assertTrue(content.contains("{") && !content.contains("123"), "Content: " + content);
    }

    @Test
    public void testModuleIsPrimitive() throws Exception {
        try (FileWriter w = new FileWriter(configFile)) {
            w.write("{ \"modules\": { \"TestModule\": \"invalid\" } }");
        }
        assertDoesNotThrow(ConfigManager::load);
        assertDoesNotThrow(ConfigManager::save);
    }
}
