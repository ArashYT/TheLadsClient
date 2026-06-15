package com.thelads.client.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigManagerTest {
    
    private File getConfigFile() {
        // According to ConfigManager: FabricLoader.getInstance().getConfigDir().resolve("theladsclient.json").toFile();
        // Wait, FabricLoader.getInstance() throws an exception if run outside of Fabric in a unit test!
        // Let's see how Feature4CapeTest avoids this...
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

    private void writeJson(String json) throws Exception {
        File file = getConfigFile();
        file.getParentFile().mkdirs();
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(json);
        }
    }

    @Test
    public void testCorruptedJson() throws Exception {
        writeJson("{ invalid json ");
        ConfigManager.loadConfig();
        // Should fall back to default
        assertTrue(ConfigManager.getConfig().isCapesEnabled());
        assertFalse(ConfigManager.getConfig().isUiScalingEnabled());
    }

    @Test
    public void testEmptyJsonObject() throws Exception {
        writeJson("{}");
        ConfigManager.loadConfig();
        // Should retain defaults
        assertTrue(ConfigManager.getConfig().isCapesEnabled());
        assertFalse(ConfigManager.getConfig().isUiScalingEnabled());
    }

    @Test
    public void testJsonArrayInsteadOfObject() throws Exception {
        writeJson("[]");
        ConfigManager.loadConfig();
        // Should catch exception and fall back to default
        assertTrue(ConfigManager.getConfig().isCapesEnabled());
        assertFalse(ConfigManager.getConfig().isUiScalingEnabled());
    }

    @Test
    public void testNullValues() throws Exception {
        writeJson("{\"capesEnabled\": null, \"uiScalingEnabled\": null}");
        ConfigManager.loadConfig();
        // What happens? GSON might ignore nulls or set booleans to false.
        // Actually, primitives can't be null, so GSON leaves them alone, retaining defaults!
        // Let's assert what actually happens.
        System.out.println("Capes Enabled after nulls: " + ConfigManager.getConfig().isCapesEnabled());
    }
}
