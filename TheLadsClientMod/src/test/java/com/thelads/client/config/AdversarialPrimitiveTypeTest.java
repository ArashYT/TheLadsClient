package com.thelads.client.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import com.thelads.client.config.ConfigManager;
import java.io.File;
import java.io.FileWriter;

public class AdversarialPrimitiveTypeTest {

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
    public void testInvalidPrimitivePreservesDefault() throws Exception {
        File file = getConfigFile();
        file.getParentFile().mkdirs();
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("{\"capesEnabled\": \"notaboolean\"}");
        }
        
        ConfigManager.loadConfig();
        
        // "notaboolean" is an invalid primitive. It should be ignored and the default (true) should be preserved.
        // However, because getAsBoolean() returns false for "notaboolean", it gets set to false!
        assertTrue(ConfigManager.getConfig().isCapesEnabled(), "Invalid primitive should not overwrite the default true with false");
    }
}
